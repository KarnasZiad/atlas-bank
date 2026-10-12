package ma.atlasbank.transaction.domain;

/**
 * PENDING until the ledger reports the outcome; COMPLETED and REJECTED are final.
 */
public enum OrderStatus {
    PENDING,
    COMPLETED,
    REJECTED
}
