package ma.atlasbank.platform.config;

import java.time.Duration;
import java.time.ZoneId;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Typed platform settings ({@code atlas.*}). Defaults live here, next to their meaning,
 * and every value can be overridden per environment.
 */
@ConfigurationProperties("atlas")
public record AtlasProperties(
        @DefaultValue Platform platform,
        @DefaultValue Datasource datasource,
        @DefaultValue Web web,
        @DefaultValue Security security,
        @DefaultValue Kafka kafka,
        @DefaultValue Events events,
        @DefaultValue OpenApi openapi) {

    /**
     * @param zoneId business time zone of the bank, used for calendar rules such as daily limits
     */
    public record Platform(@DefaultValue("Africa/Casablanca") ZoneId zoneId) {
    }

    /**
     * @param database name of the service's own database (one database per service)
     */
    public record Datasource(String database) {
    }

    /**
     * @param problemTypeBaseUri prefix of the RFC 9457 {@code type} member, completed with the error code
     */
    public record Web(@DefaultValue("urn:atlasbank:problem:") String problemTypeBaseUri) {
    }

    /**
     * @param customerIdClaim access-token claim binding a user to a customer record
     * @param publicPaths     endpoints reachable without a token (probes, metrics, API description)
     */
    public record Security(
            @DefaultValue("customer_id") String customerIdClaim,
            @DefaultValue({"/actuator/health/**", "/actuator/info", "/actuator/prometheus", "/v3/api-docs/**"})
            List<String> publicPaths) {
    }

    /**
     * @param partitions            partitions of the topics owned by the service
     * @param replicas              replication factor of the topics owned by the service
     * @param deadLetterTopicSuffix suffix of the topic receiving records that exhausted their retries
     * @param declareTopics         whether the service ensures the platform topics exist at startup
     */
    public record Kafka(
            @DefaultValue("true") boolean declareTopics,
            @DefaultValue("3") int partitions,
            @DefaultValue("1") int replicas,
            @DefaultValue(".dlt") String deadLetterTopicSuffix,
            @DefaultValue Retry retry) {

        /**
         * Exponential back-off applied before a record is sent to the dead-letter topic.
         */
        public record Retry(
                @DefaultValue("1s") Duration initialInterval,
                @DefaultValue("2.0") double multiplier,
                @DefaultValue("30s") Duration maxInterval,
                @DefaultValue("5") int maxAttempts) {
        }
    }

    /**
     * Transactional outbox maintenance.
     *
     * @param resubmissionEnabled  whether undelivered publications are retried periodically
     * @param resubmissionInterval how often incomplete event publications are retried
     * @param resubmissionDelay    minimum age of a publication before it is considered stuck
     */
    public record Events(
            @DefaultValue("true") boolean resubmissionEnabled,
            @DefaultValue("1m") Duration resubmissionInterval,
            @DefaultValue("2m") Duration resubmissionDelay) {
    }

    /**
     * @param serverUrl        public base URL of the API (the gateway), used by API consoles
     * @param authorizationUrl OAuth2 authorization endpoint advertised to API consoles
     * @param tokenUrl         OAuth2 token endpoint advertised to API consoles
     */
    public record OpenApi(String serverUrl, String authorizationUrl, String tokenUrl) {
    }
}
