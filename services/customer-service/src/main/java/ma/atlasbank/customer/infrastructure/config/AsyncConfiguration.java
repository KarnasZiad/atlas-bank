package ma.atlasbank.customer.infrastructure.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Outbox listeners run asynchronously, after the business transaction has committed.
 */
@Configuration(proxyBeanMethods = false)
@EnableAsync
class AsyncConfiguration {
}
