package ma.atlasbank.platform.autoconfigure;

import ma.atlasbank.platform.jpa.audit.AuditRevision;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurationPackage;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;

/**
 * Registers the platform's Envers revision entity next to the service's own entities, without
 * the service having to override entity scanning.
 */
@AutoConfiguration(beforeName = "org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration")
@ConditionalOnClass(name = "org.hibernate.envers.Audited")
@AutoConfigurationPackage(basePackageClasses = AuditRevision.class)
public class AtlasEnversAutoConfiguration {
}
