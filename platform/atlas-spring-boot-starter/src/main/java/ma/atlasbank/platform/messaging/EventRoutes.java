package ma.atlasbank.platform.messaging;

import java.util.function.Function;
import ma.atlasbank.contracts.IntegrationEvent;
import org.springframework.modulith.events.RoutingTarget;

/**
 * Routing helpers for the transactional outbox: an integration event goes to its contract topic,
 * keyed by its {@link IntegrationEvent#partitionKey()} so related events keep their order.
 */
public final class EventRoutes {

    private EventRoutes() {
    }

    public static <T extends IntegrationEvent> Function<T, RoutingTarget> toTopic(String topic) {
        return event -> RoutingTarget.forTarget(topic).andKey(event.partitionKey());
    }
}
