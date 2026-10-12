package ma.atlasbank.platform.autoconfigure;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import ma.atlasbank.platform.security.CurrentActor;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.data.auditing.DateTimeProvider;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * Fills the audit columns of {@link ma.atlasbank.platform.jpa.AuditableEntity} from the
 * security context and the platform clock.
 */
@AutoConfiguration(
        after = AtlasCoreAutoConfiguration.class,
        afterName = "org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration")
@ConditionalOnClass(EnableJpaAuditing.class)
@EnableJpaAuditing(auditorAwareRef = "atlasAuditorAware", dateTimeProviderRef = "atlasAuditingDateTimeProvider")
public class AtlasJpaAutoConfiguration {

    @Bean
    AuditorAware<String> atlasAuditorAware() {
        return () -> Optional.of(CurrentActor.name());
    }

    @Bean
    DateTimeProvider atlasAuditingDateTimeProvider(Clock clock) {
        return () -> Optional.of(Instant.now(clock));
    }
}
