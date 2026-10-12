package ma.atlasbank.testsupport.kafka;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import java.util.function.Predicate;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.core.ConsumerFactory;
import tools.jackson.databind.json.JsonMapper;

/**
 * Reads a topic from the beginning with a throw-away consumer group and binds the JSON payloads
 * to a contract type, until the expected events are seen or the timeout expires.
 */
public final class TopicReader {

    private final ConsumerFactory<?, ?> consumerFactory;
    private final JsonMapper jsonMapper;

    public TopicReader(ConsumerFactory<?, ?> consumerFactory, JsonMapper jsonMapper) {
        this.consumerFactory = consumerFactory;
        this.jsonMapper = jsonMapper;
    }

    public <T> List<T> await(String topic, Class<T> type, Predicate<List<T>> until, Duration timeout) {
        List<T> received = new ArrayList<>();
        long deadline = System.nanoTime() + timeout.toNanos();
        Properties fromTheBeginning = new Properties();
        fromTheBeginning.setProperty(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        try (Consumer<?, ?> consumer = consumerFactory.createConsumer(
                "test-" + UUID.randomUUID(), null, null, fromTheBeginning)) {
            consumer.subscribe(List.of(topic));
            while (!until.test(received) && System.nanoTime() < deadline) {
                for (ConsumerRecord<?, ?> consumed : consumer.poll(Duration.ofMillis(250))) {
                    received.add(jsonMapper.readValue((byte[]) consumed.value(), type));
                }
            }
        }
        return received;
    }
}
