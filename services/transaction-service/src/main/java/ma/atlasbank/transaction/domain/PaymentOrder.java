package ma.atlasbank.transaction.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
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
import ma.atlasbank.commons.money.Money;
import ma.atlasbank.platform.jpa.AuditableEntity;
import org.hibernate.annotations.UuidGenerator;

/**
 * A payment order accepted from a channel (customer app, branch teller) and tracked until the
 * ledger posts or rejects it. The order never moves money itself.
 */
@Getter
@Entity
@Table(name = "payment_order")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PaymentOrder extends AuditableEntity {

    @Id
    @GeneratedValue
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    @Column(name = "reference", nullable = false, unique = true, updatable = false, length = 40)
    private String reference;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false, updatable = false, length = 20)
    private OrderKind kind;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private OrderStatus status;

    @Column(name = "source_account_number", updatable = false, length = 34)
    private String sourceAccountNumber;

    @Column(name = "target_account_number", updatable = false, length = 34)
    private String targetAccountNumber;

    @Column(name = "amount", nullable = false, updatable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(name = "currency", nullable = false, updatable = false, length = 3)
    private Currency currency;

    @Column(name = "description", updatable = false, length = 140)
    private String description;

    @Embedded
    private Initiator initiator;

    /** Client-chosen key making retries of the same request safe ("Idempotency-Key" header). */
    @Column(name = "idempotency_key", nullable = false, updatable = false, length = 64)
    private String idempotencyKey;

    /** SHA-256 of the request content: detects a key reused for a different request. */
    @Column(name = "request_fingerprint", nullable = false, updatable = false, length = 64)
    private String requestFingerprint;

    @Column(name = "rejection_code", length = 60)
    private String rejectionCode;

    @Column(name = "requested_at", nullable = false, updatable = false)
    private Instant requestedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    public static PaymentOrder place(OrderRequest request, String reference, Initiator initiator,
                                     String idempotencyKey, String requestFingerprint, Instant requestedAt) {
        PaymentOrder order = new PaymentOrder();
        order.kind = request.kind();
        order.sourceAccountNumber = request.sourceAccountNumber();
        order.targetAccountNumber = request.targetAccountNumber();
        order.amount = request.amount().amount();
        order.currency = request.amount().currency();
        order.description = request.description();
        order.reference = Objects.requireNonNull(reference);
        order.initiator = Objects.requireNonNull(initiator);
        order.idempotencyKey = Objects.requireNonNull(idempotencyKey);
        order.requestFingerprint = Objects.requireNonNull(requestFingerprint);
        order.requestedAt = Objects.requireNonNull(requestedAt);
        order.status = OrderStatus.PENDING;
        return order;
    }

    /**
     * Applying the same outcome twice is harmless (redelivered event); a contradicting outcome
     * is a ledger inconsistency and must be investigated rather than silently applied.
     */
    public void complete(Instant at) {
        settle(OrderStatus.COMPLETED, null, at);
    }

    public void reject(String reason, Instant at) {
        settle(OrderStatus.REJECTED, Objects.requireNonNull(reason), at);
    }

    public boolean isSameRequestAs(String fingerprint) {
        return requestFingerprint.equals(fingerprint);
    }

    public Money money() {
        return Money.of(amount, currency);
    }

    private void settle(OrderStatus outcome, String reason, Instant at) {
        if (status == outcome) {
            return;
        }
        if (status != OrderStatus.PENDING) {
            throw new IllegalStateException("Order %s is already %s, cannot become %s".formatted(reference, status, outcome));
        }
        status = outcome;
        rejectionCode = reason;
        completedAt = at;
    }

    @Override
    public boolean equals(Object other) {
        return this == other || (other instanceof PaymentOrder order && id != null && id.equals(order.id));
    }

    @Override
    public int hashCode() {
        return PaymentOrder.class.hashCode();
    }
}
