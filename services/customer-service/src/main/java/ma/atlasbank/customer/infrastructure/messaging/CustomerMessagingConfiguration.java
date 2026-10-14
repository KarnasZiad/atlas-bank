package ma.atlasbank.customer.infrastructure.messaging;

import ma.atlasbank.contracts.IntegrationEvent;
import ma.atlasbank.contracts.Topics;
import ma.atlasbank.contracts.customer.ContactDetailsChanged;
import ma.atlasbank.contracts.customer.CustomerSnapshot;
import ma.atlasbank.platform.messaging.EventRoutes;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.modulith.events.EventExternalizationConfiguration;

/**
 * Customer snapshots and contact changes leave the service through the transactional outbox, keyed
 * by customer.
 */
@Configuration(proxyBeanMethods = false)
class CustomerMessagingConfiguration {

    @Bean
    EventExternalizationConfiguration eventExternalizationConfiguration() {
        return EventExternalizationConfiguration.externalizing()
                .selectByType(IntegrationEvent.class::isAssignableFrom)
                .route(CustomerSnapshot.class, EventRoutes.toTopic(Topics.CUSTOMER_SNAPSHOTS))
                .route(ContactDetailsChanged.class, EventRoutes.toTopic(Topics.CUSTOMER_CONTACT_CHANGED))
                .build();
    }
}
