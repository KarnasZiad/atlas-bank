package ma.atlasbank.contracts.customer;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import ma.atlasbank.contracts.IntegrationEvent;

/**
 * The phone number or the postal address of a customer changed. The customer is warned on a channel
 * that did not change (e-mail): an unexpected change of phone number is a classic step of an account
 * takeover. The new values are deliberately not carried.
 *
 * @param changedByCustomer whether the customer changed them online, rather than an employee
 */
public record ContactDetailsChanged(
        UUID eventId,
        Instant occurredAt,
        UUID customerId,
        Set<ContactField> changes,
        boolean changedByCustomer) implements IntegrationEvent {

    @Override
    public String partitionKey() {
        return customerId.toString();
    }

    public enum ContactField {
        PHONE_NUMBER,
        POSTAL_ADDRESS
    }
}
