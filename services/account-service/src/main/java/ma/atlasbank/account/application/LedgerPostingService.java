package ma.atlasbank.account.application;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import ma.atlasbank.account.application.port.AccountRepository;
import ma.atlasbank.account.application.port.LedgerEntryRepository;
import ma.atlasbank.account.application.port.PostingReceiptRepository;
import ma.atlasbank.account.domain.Account;
import ma.atlasbank.account.domain.AccountErrorCode;
import ma.atlasbank.account.domain.PostingReceipt;
import ma.atlasbank.account.domain.TransactionKind;
import ma.atlasbank.account.domain.TransactionReference;
import ma.atlasbank.commons.error.BusinessRuleViolationException;
import ma.atlasbank.commons.error.DomainException;
import ma.atlasbank.commons.error.OperationNotPermittedException;
import ma.atlasbank.commons.error.ResourceNotFoundException;
import ma.atlasbank.commons.money.InvalidMoneyException;
import ma.atlasbank.commons.money.Money;
import ma.atlasbank.contracts.payment.TransactionRequested;
import ma.atlasbank.contracts.payment.TransactionRequested.InitiatorType;
import ma.atlasbank.platform.time.BusinessCalendar;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Posts payment orders to the ledger. This is the only place where money moves.
 *
 * <ul>
 *   <li><b>Atomicity</b>: debit, credit, receipt and outcome event are written in one local ACID
 *       transaction; the outcome reaches Kafka through the outbox only if that transaction commits.</li>
 *   <li><b>Isolation</b>: the accounts involved are locked ({@code SELECT ... FOR UPDATE}) in
 *       ascending account-number order, so two opposite transfers can never deadlock, and balance
 *       and daily-limit checks are evaluated on locked rows.</li>
 *   <li><b>Idempotency</b>: the receipt is checked once the locks are held; a redelivered order
 *       is acknowledged without effect, and a rejected order can never succeed later.</li>
 *   <li><b>No partial state</b>: a business rejection rolls the attempt back entirely and is
 *       recorded in a separate transaction.</li>
 * </ul>
 */
@Slf4j
@Service
public class LedgerPostingService {

    private final AccountRepository accounts;
    private final LedgerEntryRepository ledger;
    private final PostingReceiptRepository receipts;
    private final AccountEventFactory events;
    private final ApplicationEventPublisher publisher;
    private final BusinessCalendar calendar;
    private final TransactionTemplate transaction;

    LedgerPostingService(AccountRepository accounts, LedgerEntryRepository ledger,
                         PostingReceiptRepository receipts, AccountEventFactory events,
                         ApplicationEventPublisher publisher, BusinessCalendar calendar,
                         PlatformTransactionManager transactionManager) {
        this.accounts = accounts;
        this.ledger = ledger;
        this.receipts = receipts;
        this.events = events;
        this.publisher = publisher;
        this.calendar = calendar;
        this.transaction = new TransactionTemplate(transactionManager);
    }

    public void post(TransactionRequested order) {
        try {
            transaction.executeWithoutResult(status -> execute(order));
        } catch (DomainException rejection) {
            log.info("Transaction {} rejected: {}", order.reference(), rejection.errorCode().code());
            transaction.executeWithoutResult(status -> reject(order, rejection));
        }
    }

    private void execute(TransactionRequested order) {
        Money amount = amountOf(order);
        LockedAccounts locked = lockAccountsOf(order);
        if (receipts.existsById(order.transactionId())) {
            log.info("Transaction {} already processed, redelivery ignored", order.reference());
            return;
        }
        authorize(order, locked);

        Instant now = calendar.now();
        TransactionReference reference = new TransactionReference(
                order.transactionId(), order.reference(), TransactionKind.valueOf(order.kind().name()), order.description());
        locked.source().ifPresent(source -> ledger.save(source.debit(
                amount, reference, debitedToday(source), order.targetAccountNumber(), now)));
        locked.target().ifPresent(target -> ledger.save(target.credit(
                amount, reference, order.sourceAccountNumber(), now)));

        receipts.save(PostingReceipt.posted(order.transactionId(), now));
        publisher.publishEvent(events.posted(order, locked.source(), locked.target()));
        log.info("Transaction {} posted", order.reference());
    }

    private void reject(TransactionRequested order, DomainException rejection) {
        if (receipts.existsById(order.transactionId())) {
            return;
        }
        receipts.save(PostingReceipt.rejected(order.transactionId(), rejection.errorCode(), calendar.now()));
        publisher.publishEvent(events.rejected(order, rejection.errorCode(),
                findAccount(order.sourceAccountNumber()), findAccount(order.targetAccountNumber())));
    }

    /**
     * Locks every account of the order in a global order (ascending account number).
     */
    private LockedAccounts lockAccountsOf(TransactionRequested order) {
        requireWellFormed(order);
        TreeMap<String, Account> locked = new TreeMap<>();
        Stream.of(order.sourceAccountNumber(), order.targetAccountNumber())
                .filter(Objects::nonNull)
                .sorted()
                .forEach(number -> locked.put(number, lock(number, order)));
        return new LockedAccounts(
                Optional.ofNullable(order.sourceAccountNumber()).map(locked::get),
                Optional.ofNullable(order.targetAccountNumber()).map(locked::get));
    }

    /**
     * A customer probing account numbers learns nothing: an unknown source account is reported
     * exactly like an account owned by someone else.
     */
    private Account lock(String accountNumber, TransactionRequested order) {
        Optional<Account> account = accounts.lockByAccountNumber(accountNumber);
        boolean customerSource = accountNumber.equals(order.sourceAccountNumber())
                && order.initiator().type() == InitiatorType.CUSTOMER;
        if (account.isEmpty() && customerSource) {
            throw notOwned(accountNumber);
        }
        return account.orElseThrow(() -> new ResourceNotFoundException(
                AccountErrorCode.ACCOUNT_NOT_FOUND, "Account %s not found", accountNumber));
    }

    /**
     * The ledger re-checks what the transaction service already validated: it never trusts an
     * upstream decision about money.
     */
    private void authorize(TransactionRequested order, LockedAccounts locked) {
        if (order.initiator().type() != InitiatorType.CUSTOMER) {
            return;
        }
        if (order.kind() != ma.atlasbank.contracts.payment.TransactionKind.TRANSFER) {
            throw new OperationNotPermittedException(AccountErrorCode.OPERATION_NOT_ALLOWED_FOR_INITIATOR,
                    "Customers can only order transfers");
        }
        Account source = locked.source().orElseThrow();
        if (!source.isOwnedBy(order.initiator().customerId())) {
            throw notOwned(source.getAccountNumber());
        }
    }

    private static void requireWellFormed(TransactionRequested order) {
        boolean wellFormed = switch (order.kind()) {
            case DEPOSIT -> order.sourceAccountNumber() == null && order.targetAccountNumber() != null;
            case WITHDRAWAL -> order.sourceAccountNumber() != null && order.targetAccountNumber() == null;
            case TRANSFER -> order.sourceAccountNumber() != null && order.targetAccountNumber() != null;
        };
        if (!wellFormed) {
            throw new BusinessRuleViolationException(AccountErrorCode.MALFORMED_ORDER,
                    "Accounts of transaction %s do not match its kind %s", order.reference(), order.kind());
        }
        if (Objects.equals(order.sourceAccountNumber(), order.targetAccountNumber())) {
            throw new BusinessRuleViolationException(AccountErrorCode.SAME_ACCOUNT_TRANSFER,
                    "Source and target accounts must differ");
        }
    }

    private static OperationNotPermittedException notOwned(String accountNumber) {
        return new OperationNotPermittedException(AccountErrorCode.SOURCE_ACCOUNT_NOT_OWNED,
                "Account %s does not belong to the ordering customer", accountNumber);
    }

    private Money debitedToday(Account account) {
        return Money.of(ledger.sumOfDebitsSince(account.getId(), calendar.startOfToday()), account.getCurrency());
    }

    private static Money amountOf(TransactionRequested order) {
        if (order.amount() == null || order.currency() == null) {
            throw invalidAmount(order);
        }
        try {
            return Money.of(order.amount(), order.currency());
        } catch (InvalidMoneyException invalid) {
            throw invalidAmount(order);
        }
    }

    private static BusinessRuleViolationException invalidAmount(TransactionRequested order) {
        return new BusinessRuleViolationException(AccountErrorCode.INVALID_AMOUNT,
                "Invalid amount for transaction %s", order.reference());
    }

    private Optional<Account> findAccount(String accountNumber) {
        return Optional.ofNullable(accountNumber).flatMap(accounts::findByAccountNumber);
    }

    private record LockedAccounts(Optional<Account> source, Optional<Account> target) {
    }
}
