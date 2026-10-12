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

    /**
     * Kafka record key: events sharing a key land on the same partition and are consumed
     * sequentially. The transactional outbox does not guarantee that events committed concurrently
     * are sent in commit order, so consumers rely on versions and idempotency, never on arrival order.
     */
    String partitionKey();
}
