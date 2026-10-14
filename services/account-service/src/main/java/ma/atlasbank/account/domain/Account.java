package ma.atlasbank.account.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;
import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import ma.atlasbank.commons.error.BusinessRuleViolationException;
import ma.atlasbank.commons.money.Money;
import ma.atlasbank.platform.jpa.AuditableEntity;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.envers.Audited;
import org.hibernate.envers.NotAudited;

/**
 * Bank account aggregate. The balance only changes through {@link #debit} and {@link #credit},
 * each producing the ledger entry that justifies the change: there is no way to alter money
 * without leaving an immutable trace.
 *
 * <p>Each balance reads in the natural direction of its account: a customer account shows what the
 * bank owes the customer (a credit increases it), the teller cash shows the cash held at the counter
 * (a debit, i.e. cash coming in, increases it). Every posting debits and credits the same amount, so
 * the customer balances always add up to the cash taken in.
 */
@Getter
@Entity
@Audited
@Table(name = "account")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Account extends AuditableEntity {

    @Id
    @GeneratedValue
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    @Column(name = "account_number", nullable = false, unique = true, updatable = false, length = 34)
    private String accountNumber;

    /** {@code null} for the internal accounts of the bank. */
    @Column(name = "customer_id", updatable = false)
    private UUID customerId;

    @Enumerated(EnumType.STRING)
    @Column(name = "account_type", nullable = false, updatable = false, length = 20)
    private AccountType type;

    @Column(name = "currency", nullable = false, updatable = false, length = 3)
    private Currency currency;

    /** Audited through the ledger, not through Envers: every change already has its own entry. */
    @NotAudited
    @Column(name = "balance", nullable = false, precision = 19, scale = 4)
    private BigDecimal balance;

    @Column(name = "overdraft_limit", nullable = false, precision = 19, scale = 4)
    private BigDecimal overdraftLimit;

    @Column(name = "daily_debit_limit", nullable = false, precision = 19, scale = 4)
    private BigDecimal dailyDebitLimit;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private AccountStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "freeze_cause", length = 30)
    private FreezeCause freezeCause;

    @Column(name = "status_reason", length = 500)
    private String statusReason;

    @Column(name = "opened_at", nullable = false, updatable = false)
    private Instant openedAt;

    @Column(name = "closed_at")
    private Instant closedAt;

    public static Account open(String accountNumber, UUID customerId, AccountType type, Currency currency,
                               AccountLimits limits, Instant openedAt) {
        Account account = new Account();
        account.accountNumber = Objects.requireNonNull(accountNumber);
        account.customerId = Objects.requireNonNull(customerId);
        account.type = Objects.requireNonNull(type);
        account.currency = Objects.requireNonNull(currency);
        account.balance = BigDecimal.ZERO;
        account.overdraftLimit = limits.overdraftLimit();
        account.dailyDebitLimit = limits.dailyDebitLimit();
        account.status = AccountStatus.ACTIVE;
        account.openedAt = Objects.requireNonNull(openedAt);
        return account;
    }

    /**
     * The cash drawer of the branch in one currency. It starts empty and can never pay out more cash
     * than it took in.
     */
    public static Account openTellerCash(String accountNumber, Currency currency, Instant openedAt) {
        Account till = new Account();
        till.accountNumber = Objects.requireNonNull(accountNumber);
        till.type = AccountType.TELLER_CASH;
        till.currency = Objects.requireNonNull(currency);
        till.balance = BigDecimal.ZERO;
        till.overdraftLimit = BigDecimal.ZERO;
        till.dailyDebitLimit = BigDecimal.ZERO;
        till.status = AccountStatus.ACTIVE;
        till.openedAt = Objects.requireNonNull(openedAt);
        return till;
    }

    public boolean isInternal() {
        return type.isInternal();
    }

    public Money balance() {
        return Money.of(balance, currency);
    }

    public Money overdraft() {
        return Money.of(overdraftLimit, currency);
    }

    public Money dailyDebitAllowance() {
        return Money.of(dailyDebitLimit, currency);
    }

    /** Funds that can leave the account right now, overdraft included. */
    public Money availableBalance() {
        return balance().add(overdraft());
    }

    /**
     * @param debitedToday total already debited during the current business day; the caller must
     *                     compute it while holding the lock on this account
     */
    public LedgerEntry debit(Money amount, TransactionReference transaction, Money debitedToday,
                             String counterparty, Instant bookedAt) {
        requireStatus(AccountStatus.ACTIVE);
        requireValid(amount);
        if (isInternal()) {
            // Cash coming into the till.
            balance = balance.add(amount.amount());
            return LedgerEntry.record(this, transaction, EntryDirection.DEBIT, amount, counterparty, bookedAt);
        }
        if (amount.isGreaterThan(availableBalance())) {
            throw new BusinessRuleViolationException(AccountErrorCode.INSUFFICIENT_FUNDS,
                    "Insufficient funds on account %s", accountNumber);
        }
        if (debitedToday.add(amount).isGreaterThan(dailyDebitAllowance())) {
            throw new BusinessRuleViolationException(AccountErrorCode.DAILY_LIMIT_EXCEEDED,
                    "Daily debit limit of %s reached on account %s", dailyDebitAllowance(), accountNumber);
        }
        balance = balance.subtract(amount.amount());
        return LedgerEntry.record(this, transaction, EntryDirection.DEBIT, amount, counterparty, bookedAt);
    }

    /**
     * Frozen accounts still receive funds; only closed accounts refuse them.
     */
    public LedgerEntry credit(Money amount, TransactionReference transaction, String counterparty, Instant bookedAt) {
        if (status == AccountStatus.CLOSED) {
            throw closed();
        }
        requireValid(amount);
        if (isInternal()) {
            // Cash handed out at the counter: only what the till holds.
            if (amount.isGreaterThan(balance())) {
                throw new BusinessRuleViolationException(AccountErrorCode.INSUFFICIENT_CASH,
                        "Not enough cash at the counter to pay out %s", amount);
            }
            balance = balance.subtract(amount.amount());
        } else {
            balance = balance.add(amount.amount());
        }
        return LedgerEntry.record(this, transaction, EntryDirection.CREDIT, amount, counterparty, bookedAt);
    }

    /**
     * Idempotent. A manual freeze takes precedence over a freeze caused by a customer suspension,
     * so that lifting the suspension later does not release it.
     */
    public void freeze(FreezeCause cause, String reason) {
        requireCustomerAccount();
        if (status == AccountStatus.CLOSED) {
            throw closed();
        }
        if (status == AccountStatus.FROZEN && cause == FreezeCause.CUSTOMER_SUSPENDED) {
            return;
        }
        status = AccountStatus.FROZEN;
        freezeCause = cause;
        statusReason = reason;
    }

    public void unfreeze() {
        requireCustomerAccount();
        requireStatus(AccountStatus.FROZEN);
        status = AccountStatus.ACTIVE;
        freezeCause = null;
        statusReason = null;
    }

    /**
     * @return whether the account was frozen because of the suspension and is now released
     */
    public boolean releaseCustomerSuspension() {
        if (status != AccountStatus.FROZEN || freezeCause != FreezeCause.CUSTOMER_SUSPENDED) {
            return false;
        }
        unfreeze();
        return true;
    }

    public void close(String reason, Instant closedAt) {
        requireCustomerAccount();
        if (status == AccountStatus.CLOSED) {
            throw closed();
        }
        if (balance.signum() != 0) {
            throw new BusinessRuleViolationException(AccountErrorCode.BALANCE_NOT_ZERO,
                    "Account %s must have a zero balance to be closed (balance: %s)", accountNumber, balance());
        }
        status = AccountStatus.CLOSED;
        freezeCause = null;
        statusReason = reason;
        this.closedAt = closedAt;
    }

    public boolean isOwnedBy(UUID customer) {
        return customerId != null && customerId.equals(customer);
    }

    private void requireCustomerAccount() {
        if (isInternal()) {
            throw new BusinessRuleViolationException(AccountErrorCode.INTERNAL_ACCOUNT,
                    "Account %s is an internal account of the bank", accountNumber);
        }
    }

    private void requireStatus(AccountStatus expected) {
        if (status == expected) {
            return;
        }
        if (status == AccountStatus.CLOSED) {
            throw closed();
        }
        if (status == AccountStatus.FROZEN) {
            throw new BusinessRuleViolationException(AccountErrorCode.ACCOUNT_FROZEN, "Account %s is frozen", accountNumber);
        }
        throw new BusinessRuleViolationException(AccountErrorCode.INVALID_STATUS_TRANSITION,
                "Account %s is %s, expected %s", accountNumber, status, expected);
    }

    private void requireValid(Money amount) {
        if (!amount.hasCurrency(currency)) {
            throw new BusinessRuleViolationException(AccountErrorCode.CURRENCY_MISMATCH,
                    "Account %s is held in %s, not %s", accountNumber, currency, amount.currency());
        }
        if (!amount.isPositive()) {
            throw new BusinessRuleViolationException(AccountErrorCode.INVALID_AMOUNT, "Amount must be positive");
        }
    }

    private BusinessRuleViolationException closed() {
        return new BusinessRuleViolationException(AccountErrorCode.ACCOUNT_CLOSED, "Account %s is closed", accountNumber);
    }

    @Override
    public boolean equals(Object other) {
        return this == other || (other instanceof Account account && id != null && id.equals(account.id));
    }

    @Override
    public int hashCode() {
        return Account.class.hashCode();
    }
}
