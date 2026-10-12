package ma.atlasbank.account.infrastructure.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Outbox delivery runs asynchronously, after the business transaction has committed.
 */
@Configuration(proxyBeanMethods = false)
@EnableAsync
class AsyncConfiguration {
}
