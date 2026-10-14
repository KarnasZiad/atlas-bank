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
import java.util.Optional;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import ma.atlasbank.commons.error.BusinessRuleViolationException;
import ma.atlasbank.commons.error.OperationNotPermittedException;
import ma.atlasbank.commons.money.Money;
import ma.atlasbank.platform.jpa.AuditableEntity;
import org.hibernate.annotations.UuidGenerator;

/**
 * A payment order accepted from a channel (customer app, branch teller) and tracked until the
 * ledger posts or rejects it. The order never moves money itself.
 *
 * <p>Staff orders above the four-eyes limits are held until a <em>different</em> employee approves
 * them; only then are they handed to the ledger.
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

    /** Set at intake: the order exceeded the limits an employee may use alone. */
    @Column(name = "approval_required", nullable = false, updatable = false)
    private boolean approvalRequired;

    @Embedded
    private Reviewer reviewer;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    /**
     * @param approvalRequired whether a second employee must approve the order before it reaches the ledger
     */
    public static PaymentOrder place(OrderRequest request, String reference, Initiator initiator,
                                     String idempotencyKey, String requestFingerprint, Instant requestedAt,
                                     boolean approvalRequired) {
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
        order.approvalRequired = approvalRequired;
        order.status = approvalRequired ? OrderStatus.AWAITING_APPROVAL : OrderStatus.PENDING;
        return order;
    }

    /**
     * Four-eyes principle: whoever placed the order cannot be the one who approves it.
     */
    public void approve(Reviewer approver, Instant at) {
        requireAwaitingApproval();
        if (approver.getSubject().equals(initiator.getSubject())) {
            throw new OperationNotPermittedException(TransactionErrorCode.SELF_APPROVAL_FORBIDDEN,
                    "Order %s must be approved by another employee than the one who placed it", reference);
        }
        status = OrderStatus.PENDING;
        reviewer = approver;
        reviewedAt = at;
    }

    /**
     * Refusing is always allowed, including by the employee who placed the order (who withdraws it).
     */
    public void refuse(Reviewer refuser, Instant at) {
        requireAwaitingApproval();
        status = OrderStatus.REJECTED;
        rejectionCode = TransactionErrorCode.REFUSED_BY_REVIEWER.code();
        reviewer = refuser;
        reviewedAt = at;
        completedAt = at;
    }

    /**
     * An approval is only valid on the business day the order was placed: an order still unreviewed at
     * the end of that day is rejected, never executed on a stale decision.
     */
    public void expire(Instant at) {
        requireAwaitingApproval();
        status = OrderStatus.REJECTED;
        rejectionCode = TransactionErrorCode.APPROVAL_EXPIRED.code();
        completedAt = at;
    }

    /** Employee who approved the order, when a review was required and granted. */
    public Optional<Reviewer> approver() {
        boolean refused = TransactionErrorCode.REFUSED_BY_REVIEWER.code().equals(rejectionCode);
        return approvalRequired && reviewer != null && !refused ? Optional.of(reviewer) : Optional.empty();
    }

    private void requireAwaitingApproval() {
        if (status != OrderStatus.AWAITING_APPROVAL) {
            throw new BusinessRuleViolationException(TransactionErrorCode.ORDER_NOT_AWAITING_APPROVAL,
                    "Order %s is %s, it is not awaiting approval", reference, status);
        }
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
