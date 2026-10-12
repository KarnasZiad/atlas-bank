package ma.atlasbank.account.application;

import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import ma.atlasbank.account.domain.Account;
import ma.atlasbank.commons.error.ErrorCode;
import ma.atlasbank.contracts.account.AccountOpened;
import ma.atlasbank.contracts.account.AccountStatus;
import ma.atlasbank.contracts.account.AccountStatusChanged;
import ma.atlasbank.contracts.account.AccountType;
import ma.atlasbank.contracts.ledger.TransactionOutcome;
import ma.atlasbank.contracts.ledger.TransactionOutcome.Party;
import ma.atlasbank.contracts.ledger.TransactionOutcome.PostingResult;
import ma.atlasbank.contracts.payment.TransactionRequested;
import ma.atlasbank.platform.time.BusinessCalendar;
import org.springframework.stereotype.Component;

/**
 * Translates ledger facts into the published language.
 */
@Component
@RequiredArgsConstructor
class AccountEventFactory {

    private final BusinessCalendar calendar;

    AccountOpened opened(Account account) {
        return new AccountOpened(UUID.randomUUID(), calendar.now(), account.getId(), account.getAccountNumber(),
                account.getCustomerId(), AccountType.valueOf(account.getType().name()),
                account.getCurrency().getCurrencyCode());
    }

    AccountStatusChanged statusChanged(Account account, ma.atlasbank.account.domain.AccountStatus previous) {
        return new AccountStatusChanged(UUID.randomUUID(), calendar.now(), account.getId(), account.getAccountNumber(),
                account.getCustomerId(), AccountStatus.valueOf(previous.name()),
                AccountStatus.valueOf(account.getStatus().name()), account.getStatusReason());
    }

    TransactionOutcome posted(TransactionRequested order, Optional<Account> debited, Optional<Account> credited) {
        return outcome(order, PostingResult.POSTED, null, debited, credited);
    }

    TransactionOutcome rejected(TransactionRequested order, ErrorCode reason,
                                Optional<Account> debited, Optional<Account> credited) {
        return outcome(order, PostingResult.REJECTED, reason.code(), debited, credited);
    }

    private TransactionOutcome outcome(TransactionRequested order, PostingResult result, String rejectionCode,
                                       Optional<Account> debited, Optional<Account> credited) {
        return new TransactionOutcome(
                UUID.randomUUID(),
                calendar.now(),
                order.transactionId(),
                order.reference(),
                order.kind(),
                result,
                rejectionCode,
                order.amount(),
                order.currency(),
                party(order.sourceAccountNumber(), debited),
                party(order.targetAccountNumber(), credited),
                order.initiator().customerId());
    }

    private static Party party(String accountNumber, Optional<Account> account) {
        if (accountNumber == null) {
            return null;
        }
        return new Party(accountNumber, account.map(Account::getCustomerId).orElse(null));
    }
}
