package ma.atlasbank.account.domain;

import java.util.Objects;
import java.util.UUID;

/**
 * Identity of the payment order being posted, carried by every ledger entry it produces.
 */
public record TransactionReference(UUID transactionId, String reference, TransactionKind kind, String description) {

    public TransactionReference {
        Objects.requireNonNull(transactionId, "transactionId");
        Objects.requireNonNull(reference, "reference");
        Objects.requireNonNull(kind, "kind");
    }
}
