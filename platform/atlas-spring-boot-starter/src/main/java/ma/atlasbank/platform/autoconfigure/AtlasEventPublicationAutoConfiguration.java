package ma.atlasbank.platform.autoconfigure;

import java.time.Duration;
import ma.atlasbank.platform.config.AtlasProperties;
import ma.atlasbank.platform.messaging.EventPublicationResubmitter;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.modulith.events.IncompleteEventPublications;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.FixedDelayTask;

/**
 * Keeps the transactional outbox flowing: publications whose delivery failed (broker down,
 * listener error) are periodically resubmitted instead of waiting for the next restart.
 */
@AutoConfiguration(afterName = "org.springframework.modulith.events.config.EventPublicationAutoConfiguration")
@ConditionalOnClass(IncompleteEventPublications.class)
@ConditionalOnBean(IncompleteEventPublications.class)
@ConditionalOnProperty(prefix = "atlas.events", name = "resubmission-enabled", matchIfMissing = true)
@EnableScheduling
public class AtlasEventPublicationAutoConfiguration {

    @Bean
    EventPublicationResubmitter eventPublicationResubmitter(
            IncompleteEventPublications incompletePublications, AtlasProperties properties) {
        return new EventPublicationResubmitter(incompletePublications, properties.events());
    }

    @Bean
    SchedulingConfigurer eventPublicationResubmissionSchedule(
            EventPublicationResubmitter resubmitter, AtlasProperties properties) {
        Duration interval = properties.events().resubmissionInterval();
        return registrar -> registrar.addFixedDelayTask(
                new FixedDelayTask(resubmitter::resubmitIncompletePublications, interval, interval));
    }
}
