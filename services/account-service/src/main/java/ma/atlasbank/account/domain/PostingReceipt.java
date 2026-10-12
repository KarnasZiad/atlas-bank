package ma.atlasbank.account.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import ma.atlasbank.commons.error.ErrorCode;
import org.hibernate.annotations.Immutable;

/**
 * Proof that a payment order has been processed by the ledger, whatever the outcome.
 *
 * <p>This is the idempotency record of the consumer ("inbox"): a redelivered order finds its
 * receipt and is ignored, so money is never moved twice and a rejected order never succeeds later.
 */
@Getter
@Entity
@Immutable
@Table(name = "posting_receipt")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PostingReceipt {

    @Id
    @Column(name = "transaction_id", nullable = false, updatable = false)
    private UUID transactionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "result", nullable = false, updatable = false, length = 10)
    private PostingResult result;

    @Column(name = "rejection_code", updatable = false, length = 60)
    private String rejectionCode;

    @Column(name = "processed_at", nullable = false, updatable = false)
    private Instant processedAt;

    public static PostingReceipt posted(UUID transactionId, Instant processedAt) {
        return create(transactionId, PostingResult.POSTED, null, processedAt);
    }

    public static PostingReceipt rejected(UUID transactionId, ErrorCode reason, Instant processedAt) {
        return create(transactionId, PostingResult.REJECTED, reason.code(), processedAt);
    }

    private static PostingReceipt create(UUID transactionId, PostingResult result, String rejectionCode, Instant at) {
        PostingReceipt receipt = new PostingReceipt();
        receipt.transactionId = transactionId;
        receipt.result = result;
        receipt.rejectionCode = rejectionCode;
        receipt.processedAt = at;
        return receipt;
    }

    public enum PostingResult {
        POSTED,
        REJECTED
    }
}
