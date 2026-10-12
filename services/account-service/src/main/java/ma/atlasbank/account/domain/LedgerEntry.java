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
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import ma.atlasbank.commons.money.Money;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.UuidGenerator;

/**
 * One movement on one account. Entries are append-only (Hibernate refuses to update them) and
 * record the resulting balance, so a statement can be produced without any recomputation.
 */
@Getter
@Entity
@Immutable
@Table(name = "ledger_entry")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LedgerEntry {

    @Id
    @GeneratedValue
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    @Column(name = "account_id", nullable = false, updatable = false)
    private UUID accountId;

    @Column(name = "transaction_id", nullable = false, updatable = false)
    private UUID transactionId;

    @Column(name = "transaction_reference", nullable = false, updatable = false, length = 40)
    private String transactionReference;

    @Enumerated(EnumType.STRING)
    @Column(name = "transaction_kind", nullable = false, updatable = false, length = 20)
    private TransactionKind kind;

    @Enumerated(EnumType.STRING)
    @Column(name = "direction", nullable = false, updatable = false, length = 10)
    private EntryDirection direction;

    @Column(name = "amount", nullable = false, updatable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(name = "currency", nullable = false, updatable = false, length = 3)
    private Currency currency;

    @Column(name = "balance_after", nullable = false, updatable = false, precision = 19, scale = 4)
    private BigDecimal balanceAfter;

    @Column(name = "counterparty_account_number", updatable = false, length = 34)
    private String counterpartyAccountNumber;

    @Column(name = "description", updatable = false, length = 140)
    private String description;

    @Column(name = "booked_at", nullable = false, updatable = false)
    private Instant bookedAt;

    static LedgerEntry record(Account account, TransactionReference transaction, EntryDirection direction,
                              Money amount, String counterparty, Instant bookedAt) {
        LedgerEntry entry = new LedgerEntry();
        entry.accountId = account.getId();
        entry.transactionId = transaction.transactionId();
        entry.transactionReference = transaction.reference();
        entry.kind = transaction.kind();
        entry.description = transaction.description();
        entry.direction = direction;
        entry.amount = amount.amount();
        entry.currency = amount.currency();
        entry.balanceAfter = account.balance().amount();
        entry.counterpartyAccountNumber = counterparty;
        entry.bookedAt = bookedAt;
        return entry;
    }

    public Money money() {
        return Money.of(amount, currency);
    }
}
