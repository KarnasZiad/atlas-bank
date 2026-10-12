package ma.atlasbank.platform.autoconfigure;

import io.micrometer.tracing.Tracer;
import java.time.Clock;
import ma.atlasbank.platform.config.AtlasProperties;
import ma.atlasbank.platform.observability.MicrometerTraceIdProvider;
import ma.atlasbank.platform.observability.TraceIdProvider;
import ma.atlasbank.platform.time.BusinessCalendar;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Platform beans needed by every service, whatever its stack.
 */
@AutoConfiguration
@EnableConfigurationProperties(AtlasProperties.class)
public class AtlasCoreAutoConfiguration {

    /**
     * Single source of time: business code never calls {@code Instant.now()} directly, which keeps
     * time-based rules (daily limits, expirations) deterministic in tests.
     */
    @Bean
    @ConditionalOnMissingBean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    @ConditionalOnMissingBean
    BusinessCalendar businessCalendar(Clock clock, AtlasProperties properties) {
        return new BusinessCalendar(clock, properties.platform().zoneId());
    }

    @Bean
    @ConditionalOnMissingBean
    TraceIdProvider traceIdProvider(ObjectProvider<Tracer> tracer) {
        return new MicrometerTraceIdProvider(tracer);
    }
}
