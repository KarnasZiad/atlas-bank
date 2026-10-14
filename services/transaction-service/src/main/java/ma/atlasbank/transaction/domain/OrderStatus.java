package ma.atlasbank.transaction.domain;

/**
 * <pre>
 * AWAITING_APPROVAL ──approve──► PENDING ──ledger──► COMPLETED | REJECTED
 *        └───────refuse────────────────────────────► REJECTED
 * </pre>
 * Staff orders above the four-eyes limits wait for a second employee; every other order starts
 * PENDING. COMPLETED and REJECTED are final.
 */
public enum OrderStatus {
    AWAITING_APPROVAL,
    PENDING,
    COMPLETED,
    REJECTED
}
