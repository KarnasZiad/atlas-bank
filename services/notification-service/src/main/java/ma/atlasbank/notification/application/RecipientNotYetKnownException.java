package ma.atlasbank.notification.application;

import java.util.UUID;

/**
 * The event concerns a customer whose snapshot has not been received yet (topics are consumed
 * independently). Transient by nature: the event is retried with back-off.
 */
public class RecipientNotYetKnownException extends RuntimeException {

    public RecipientNotYetKnownException(UUID customerId) {
        super("No contact details known yet for customer " + customerId);
    }
}
