package ma.atlasbank.testsupport.containers;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Real PostgreSQL and Kafka for integration tests, wired through Spring Boot service connections
 * (no connection properties to keep in sync). Images are those of the runtime stack ({@link ComposeImages}).
 *
 * <p>Usage: {@code @SpringBootTest @Import(InfrastructureContainers.class)}.
 */
@TestConfiguration(proxyBeanMethods = false)
public class InfrastructureContainers {

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgres() {
        return new PostgreSQLContainer(ComposeImages.of("postgres"));
    }

    @Bean
    @ServiceConnection
    KafkaContainer kafka() {
        return new KafkaContainer(ComposeImages.of("kafka"));
    }
}
