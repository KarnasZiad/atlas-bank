package ma.atlasbank.contracts.customer;

import java.time.Instant;
import java.util.UUID;
import ma.atlasbank.contracts.IntegrationEvent;

/**
 * Full, current state of a customer, published after every change.
 *
 * <p>Consumers keep a local projection and apply a snapshot only when its {@code version}
 * is newer than the one they hold, which makes processing idempotent and order-tolerant.
 */
public record CustomerSnapshot(
        UUID eventId,
        Instant occurredAt,
        UUID customerId,
        long version,
        String firstName,
        String lastName,
        String email,
        String phoneNumber,
        String preferredLanguage,
        CustomerStatus status) implements IntegrationEvent {

    @Override
    public String partitionKey() {
        return customerId.toString();
    }
}
