package ma.atlasbank.testsupport.containers;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Real PostgreSQL and Kafka for integration tests, wired through Spring Boot service connections
 * (no connection properties to keep in sync). Images match the ones of the runtime stack.
 *
 * <p>Usage: {@code @SpringBootTest @Import(InfrastructureContainers.class)}.
 */
@TestConfiguration(proxyBeanMethods = false)
public class InfrastructureContainers {

    public static final DockerImageName POSTGRES_IMAGE = DockerImageName.parse("postgres:18.6-alpine");
    public static final DockerImageName KAFKA_IMAGE = DockerImageName.parse("apache/kafka:4.3.1");

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgres() {
        return new PostgreSQLContainer(POSTGRES_IMAGE);
    }

    @Bean
    @ServiceConnection
    KafkaContainer kafka() {
        return new KafkaContainer(KAFKA_IMAGE);
    }
}
