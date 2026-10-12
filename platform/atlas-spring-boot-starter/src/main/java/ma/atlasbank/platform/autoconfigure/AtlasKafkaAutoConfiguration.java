package ma.atlasbank.platform.autoconfigure;

import jakarta.validation.ValidationException;
import ma.atlasbank.commons.error.DomainException;
import ma.atlasbank.platform.config.AtlasProperties;
import ma.atlasbank.platform.messaging.PlatformTopics;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.TopicPartition;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.converter.ByteArrayJacksonJsonMessageConverter;
import org.springframework.kafka.support.converter.RecordMessageConverter;
import org.springframework.util.backoff.ExponentialBackOff;
import tools.jackson.databind.json.JsonMapper;

/**
 * Messaging conventions:
 * <ul>
 *   <li>payloads travel as JSON bytes produced and consumed by a single message converter; consumers
 *       bind them to the listener's parameter type, so no class name travels on the wire and no
 *       type-header driven deserialization can be abused;</li>
 *   <li>transient failures are retried with exponential back-off, then the original bytes are
 *       parked on a dead-letter topic for inspection and replay instead of blocking the partition.</li>
 * </ul>
 */
@AutoConfiguration(
        after = AtlasCoreAutoConfiguration.class,
        beforeName = "org.springframework.boot.kafka.autoconfigure.KafkaAutoConfiguration")
@ConditionalOnClass(KafkaTemplate.class)
public class AtlasKafkaAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(RecordMessageConverter.class)
    RecordMessageConverter atlasKafkaMessageConverter(JsonMapper jsonMapper) {
        return new ByteArrayJacksonJsonMessageConverter(jsonMapper);
    }

    @Bean
    @ConditionalOnProperty(prefix = "atlas.kafka", name = "declare-topics", matchIfMissing = true)
    KafkaAdmin.NewTopics atlasPlatformTopics(AtlasProperties properties) {
        return new KafkaAdmin.NewTopics(PlatformTopics.all(properties.kafka()).toArray(NewTopic[]::new));
    }

    @Bean
    @ConditionalOnMissingBean(CommonErrorHandler.class)
    DefaultErrorHandler atlasKafkaErrorHandler(KafkaOperations<?, ?> kafkaOperations, AtlasProperties properties) {
        AtlasProperties.Kafka kafka = properties.kafka();
        // The failed record is republished untouched (raw bytes) with diagnostic headers.
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(
                kafkaOperations,
                (consumed, failure) -> new TopicPartition(consumed.topic() + kafka.deadLetterTopicSuffix(), -1));

        AtlasProperties.Kafka.Retry retry = kafka.retry();
        ExponentialBackOff backOff = new ExponentialBackOff(retry.initialInterval().toMillis(), retry.multiplier());
        backOff.setMaxInterval(retry.maxInterval().toMillis());
        backOff.setMaxAttempts(retry.maxAttempts());

        DefaultErrorHandler errorHandler = new DefaultErrorHandler(recoverer, backOff);
        // Deterministic failures: retrying cannot succeed, so go straight to the dead-letter topic.
        errorHandler.addNotRetryableExceptions(DomainException.class, ValidationException.class);
        return errorHandler;
    }
}
