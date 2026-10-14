package ma.atlasbank.account.application;

import java.time.Instant;
import java.util.Currency;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import ma.atlasbank.account.application.port.AccountNumberGenerator;
import ma.atlasbank.account.application.port.AccountRepository;
import ma.atlasbank.account.application.port.LedgerEntryRepository;
import ma.atlasbank.account.application.port.OrderAuthenticator;
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
import ma.atlasbank.commons.security.UntrustedMessageException;
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
 *   <li><b>Authenticity</b>: only orders signed by the transaction service are considered; the
 *       initiator and the approval they carry decide what is allowed, so they must be genuine.</li>
 *   <li><b>Double entry</b>: every order debits one account and credits another for the same
 *       amount; cash deposits and withdrawals have the branch till as counterpart.</li>
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
    private final AccountNumberGenerator accountNumbers;
    private final OrderAuthenticator authenticator;
    private final AccountEventFactory events;
    private final ApplicationEventPublisher publisher;
    private final BusinessCalendar calendar;
    private final TransactionTemplate transaction;

    LedgerPostingService(AccountRepository accounts, LedgerEntryRepository ledger,
                         PostingReceiptRepository receipts, AccountNumberGenerator accountNumbers,
                         OrderAuthenticator authenticator, AccountEventFactory events,
                         ApplicationEventPublisher publisher, BusinessCalendar calendar,
                         PlatformTransactionManager transactionManager) {
        this.accounts = accounts;
        this.ledger = ledger;
        this.receipts = receipts;
        this.accountNumbers = accountNumbers;
        this.authenticator = authenticator;
        this.events = events;
        this.publisher = publisher;
        this.calendar = calendar;
        this.transaction = new TransactionTemplate(transactionManager);
    }

    /**
     * @throws UntrustedMessageException when the order is not signed by the transaction service: it is
     *                                   left untouched (no receipt), so that a forged order can never
     *                                   pre-empt a genuine one carrying the same identifier
     */
    public void post(TransactionRequested order) {
        if (!authenticator.isAuthentic(order)) {
            log.error("SECURITY: payment order {} ({}) has no valid signature, refused without effect",
                    order.reference(), order.transactionId());
            throw new UntrustedMessageException("Payment order %s is not signed by the transaction service"
                    .formatted(order.reference()));
        }
        try {
            transaction.executeWithoutResult(status -> execute(order));
        } catch (DomainException rejection) {
            log.info("Transaction {} rejected: {}", order.reference(), rejection.errorCode().code());
            transaction.executeWithoutResult(status -> reject(order, rejection));
        }
    }

    private void execute(TransactionRequested order) {
        Money amount = amountOf(order);
        LockedAccounts locked = lockAccountsOf(order, amount.currency());
        if (receipts.existsById(order.transactionId())) {
            log.info("Transaction {} already processed, redelivery ignored", order.reference());
            return;
        }
        authorize(order, locked);

        Instant now = calendar.now();
        TransactionReference reference = new TransactionReference(
                order.transactionId(), order.reference(), TransactionKind.valueOf(order.kind().name()), order.description());
        Account debited = locked.debited();
        Account credited = locked.credited();
        ledger.save(debited.debit(amount, reference, debitedToday(debited), counterpartyOf(credited), now));
        ledger.save(credited.credit(amount, reference, counterpartyOf(debited), now));

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
     * Locks every account of the order in a global order (ascending account number), the till of a
     * cash operation included.
     */
    private LockedAccounts lockAccountsOf(TransactionRequested order, Currency currency) {
        requireWellFormed(order);
        String till = order.kind() == ma.atlasbank.contracts.payment.TransactionKind.TRANSFER
                ? null : accountNumbers.tellerCashAccountNumber(currency);
        if (till != null && (till.equals(order.sourceAccountNumber()) || till.equals(order.targetAccountNumber()))) {
            throw notFound(till);
        }
        TreeMap<String, Account> locked = new TreeMap<>();
        Stream.of(order.sourceAccountNumber(), order.targetAccountNumber(), till)
                .filter(Objects::nonNull)
                .sorted()
                .forEach(number -> locked.put(number, number.equals(till) ? lockTill(till, currency) : lock(number, order)));
        return new LockedAccounts(
                Optional.ofNullable(order.sourceAccountNumber()).map(locked::get),
                Optional.ofNullable(order.targetAccountNumber()).map(locked::get),
                Optional.ofNullable(till).map(locked::get));
    }

    /**
     * A customer probing account numbers learns nothing: an unknown source account is reported
     * exactly like an account owned by someone else. Internal accounts cannot be named in an order.
     */
    private Account lock(String accountNumber, TransactionRequested order) {
        Optional<Account> account = accounts.lockByAccountNumber(accountNumber).filter(found -> !found.isInternal());
        boolean customerSource = accountNumber.equals(order.sourceAccountNumber())
                && order.initiator().type() == InitiatorType.CUSTOMER;
        if (account.isEmpty() && customerSource) {
            throw notOwned(accountNumber);
        }
        return account.orElseThrow(() -> notFound(accountNumber));
    }

    /**
     * The till is opened on its first cash operation. Two first operations racing would both insert
     * it: one fails on the unique account number and is retried, then finds the till.
     */
    private Account lockTill(String accountNumber, Currency currency) {
        return accounts.lockByAccountNumber(accountNumber)
                .orElseGet(() -> accounts.saveAndFlush(Account.openTellerCash(accountNumber, currency, calendar.now())));
    }

    /**
     * The ledger re-checks what the transaction service already validated: it never trusts an
     * upstream decision about money.
     */
    private void authorize(TransactionRequested order, LockedAccounts locked) {
        if (order.initiator().type() != InitiatorType.CUSTOMER) {
            requireFourEyes(order);
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

    /** An approval, when a staff order carries one, must come from someone else than its initiator. */
    private static void requireFourEyes(TransactionRequested order) {
        if (order.approval() != null && Objects.equals(order.approval().subject(), order.initiator().subject())) {
            throw new OperationNotPermittedException(AccountErrorCode.FOUR_EYES_VIOLATION,
                    "Order %s was approved by the employee who placed it", order.reference());
        }
    }

    private static void requireWellFormed(TransactionRequested order) {
        boolean wellFormed = order.initiator() != null && order.initiator().type() != null && switch (order.kind()) {
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

    private static ResourceNotFoundException notFound(String accountNumber) {
        return new ResourceNotFoundException(AccountErrorCode.ACCOUNT_NOT_FOUND, "Account %s not found", accountNumber);
    }

    /** Daily limits protect customer accounts; the till has none. */
    private Money debitedToday(Account account) {
        if (account.isInternal()) {
            return Money.zero(account.getCurrency());
        }
        return Money.of(ledger.sumOfDebitsSince(account.getId(), calendar.startOfToday()), account.getCurrency());
    }

    /** Customer statements show the other customer account of a transfer, never an internal account. */
    private static String counterpartyOf(Account other) {
        return other.isInternal() ? null : other.getAccountNumber();
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
        return Optional.ofNullable(accountNumber).flatMap(accounts::findByAccountNumber).filter(found -> !found.isInternal());
    }

    /**
     * @param source customer account debited, absent for a deposit
     * @param target customer account credited, absent for a withdrawal
     * @param till   branch cash, counterpart of a deposit or a withdrawal
     */
    private record LockedAccounts(Optional<Account> source, Optional<Account> target, Optional<Account> till) {

        Account debited() {
            return source.or(() -> till).orElseThrow();
        }

        Account credited() {
            return target.or(() -> till).orElseThrow();
        }
    }
}
