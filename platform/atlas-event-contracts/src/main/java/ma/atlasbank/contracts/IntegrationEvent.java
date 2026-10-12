package ma.atlasbank.contracts;

import java.time.Instant;
import java.util.UUID;

/**
 * Integration event exchanged between services.
 *
 * <p>Events are immutable facts. They never carry behaviour, so sharing this module couples
 * services to a vocabulary (the "published language"), not to each other's code.
 */
public interface IntegrationEvent {

    /** Unique identifier, used by consumers for de-duplication. */
    UUID eventId();

    /** Business time at which the fact happened. */
    Instant occurredAt();

    /** Kafka record key: events sharing a key are delivered in order. */
    String partitionKey();
}
