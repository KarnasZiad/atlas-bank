package ma.atlasbank.platform.messaging;

import java.util.List;
import java.util.stream.Stream;
import ma.atlasbank.contracts.Topics;
import ma.atlasbank.platform.config.AtlasProperties;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.config.TopicConfig;
import org.springframework.kafka.config.TopicBuilder;

/**
 * The Kafka topology of the platform, declared once. Every service ensures it exists at startup
 * (creation is idempotent), so services can start in any order and a consumer never subscribes to
 * a topic that a producer has not created yet. Each topic gets its dead-letter companion.
 */
public final class PlatformTopics {

    private PlatformTopics() {
    }

    public static List<NewTopic> all(AtlasProperties.Kafka kafka) {
        return Stream.of(
                        compacted(Topics.CUSTOMER_SNAPSHOTS, kafka),
                        regular(Topics.CUSTOMER_CONTACT_CHANGED, kafka),
                        regular(Topics.ACCOUNT_OPENED, kafka),
                        regular(Topics.ACCOUNT_STATUS_CHANGED, kafka),
                        regular(Topics.TRANSACTION_REQUESTED, kafka),
                        regular(Topics.TRANSACTION_OUTCOMES, kafka))
                .flatMap(topic -> Stream.of(topic, regular(topic.name() + kafka.deadLetterTopicSuffix(), kafka)))
                .toList();
    }

    private static NewTopic regular(String name, AtlasProperties.Kafka kafka) {
        return builder(name, kafka).build();
    }

    /**
     * Log compaction keeps the latest record per key: the topic is a replayable state store. Superseded
     * records are guaranteed to be compacted away within the maximum lag, so personal data removed from
     * the latest snapshot (closed customers) does not linger in older ones.
     */
    private static NewTopic compacted(String name, AtlasProperties.Kafka kafka) {
        return builder(name, kafka).compact()
                .config(TopicConfig.MAX_COMPACTION_LAG_MS_CONFIG, String.valueOf(kafka.maxCompactionLag().toMillis()))
                .build();
    }

    private static TopicBuilder builder(String name, AtlasProperties.Kafka kafka) {
        return TopicBuilder.name(name).partitions(kafka.partitions()).replicas(kafka.replicas());
    }
}
