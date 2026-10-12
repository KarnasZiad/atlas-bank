package ma.atlasbank.account.infrastructure.messaging;

import ma.atlasbank.contracts.IntegrationEvent;
import ma.atlasbank.contracts.Topics;
import ma.atlasbank.contracts.account.AccountOpened;
import ma.atlasbank.contracts.account.AccountStatusChanged;
import ma.atlasbank.contracts.ledger.TransactionOutcome;
import ma.atlasbank.platform.messaging.EventRoutes;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.modulith.events.EventExternalizationConfiguration;

/**
 * Account lifecycle events and posting outcomes leave the service through the outbox.
 */
@Configuration(proxyBeanMethods = false)
class AccountMessagingConfiguration {

    @Bean
    EventExternalizationConfiguration eventExternalizationConfiguration() {
        return EventExternalizationConfiguration.externalizing()
                .selectByType(IntegrationEvent.class::isAssignableFrom)
                .route(AccountOpened.class, EventRoutes.toTopic(Topics.ACCOUNT_OPENED))
                .route(AccountStatusChanged.class, EventRoutes.toTopic(Topics.ACCOUNT_STATUS_CHANGED))
                .route(TransactionOutcome.class, EventRoutes.toTopic(Topics.TRANSACTION_OUTCOMES))
                .build();
    }
}
