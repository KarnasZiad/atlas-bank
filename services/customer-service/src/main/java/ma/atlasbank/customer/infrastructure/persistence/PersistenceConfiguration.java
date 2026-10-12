package ma.atlasbank.customer.infrastructure.persistence;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.envers.repository.support.EnversRevisionRepositoryFactoryBean;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * Enables Envers-backed revision queries on the Spring Data repositories.
 */
@Configuration(proxyBeanMethods = false)
@EnableJpaRepositories(repositoryFactoryBeanClass = EnversRevisionRepositoryFactoryBean.class)
class PersistenceConfiguration {
}
