package ma.atlasbank.transaction.infrastructure.messaging;

import ma.atlasbank.contracts.Topics;
import ma.atlasbank.contracts.payment.TransactionRequested;
import ma.atlasbank.platform.messaging.EventRoutes;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.modulith.events.EventExternalizationConfiguration;

/**
 * Accepted orders leave the service through the outbox, keyed by the debited account so that
 * all orders of an account are processed in sequence by the ledger.
 */
@Configuration(proxyBeanMethods = false)
class TransactionMessagingConfiguration {

    @Bean
    EventExternalizationConfiguration eventExternalizationConfiguration() {
        return EventExternalizationConfiguration.externalizing()
                .selectByType(TransactionRequested.class)
                .route(TransactionRequested.class, EventRoutes.toTopic(Topics.TRANSACTION_REQUESTED))
                .build();
    }
}
