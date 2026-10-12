package ma.atlasbank.customer.infrastructure.messaging;

import ma.atlasbank.contracts.Topics;
import ma.atlasbank.contracts.customer.CustomerSnapshot;
import ma.atlasbank.platform.messaging.EventRoutes;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.modulith.events.EventExternalizationConfiguration;

/**
 * Customer snapshots leave the service through the transactional outbox, keyed by customer.
 */
@Configuration(proxyBeanMethods = false)
class CustomerMessagingConfiguration {

    @Bean
    EventExternalizationConfiguration eventExternalizationConfiguration() {
        return EventExternalizationConfiguration.externalizing()
                .selectByType(CustomerSnapshot.class)
                .route(CustomerSnapshot.class, EventRoutes.toTopic(Topics.CUSTOMER_SNAPSHOTS))
                .build();
    }
}
