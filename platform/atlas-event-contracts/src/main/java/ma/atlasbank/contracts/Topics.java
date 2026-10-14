package ma.atlasbank.contracts;

/**
 * Kafka topic names. The {@code .v1} suffix is part of the contract: a breaking change to an
 * event ships on a new topic so that producers and consumers can migrate independently.
 */
public final class Topics {

    /** Compacted topic holding the latest state of every customer (event-carried state transfer). */
    public static final String CUSTOMER_SNAPSHOTS = "atlas.customer.snapshots.v1";

    /** Security warnings to customers: their contact details changed. */
    public static final String CUSTOMER_CONTACT_CHANGED = "atlas.customer.contact-changed.v1";

    public static final String ACCOUNT_OPENED = "atlas.account.opened.v1";

    public static final String ACCOUNT_STATUS_CHANGED = "atlas.account.status-changed.v1";

    public static final String TRANSACTION_REQUESTED = "atlas.payment.transaction-requested.v1";

    public static final String TRANSACTION_OUTCOMES = "atlas.ledger.transaction-outcomes.v1";

    private Topics() {
    }
}
