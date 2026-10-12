package ma.atlasbank.platform.autoconfigure;

import java.time.Clock;
import java.util.List;
import ma.atlasbank.platform.config.AtlasProperties;
import ma.atlasbank.platform.observability.TraceIdProvider;
import ma.atlasbank.platform.security.RequesterArgumentResolver;
import ma.atlasbank.platform.security.RequesterResolver;
import ma.atlasbank.platform.web.ProblemDetailEnricher;
import ma.atlasbank.platform.web.ProblemDetailsExceptionHandler;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Uniform HTTP behaviour: RFC 9457 error responses and {@code Requester} injection in controllers.
 */
@AutoConfiguration(
        after = {AtlasCoreAutoConfiguration.class, AtlasSecurityAutoConfiguration.class},
        beforeName = "org.springframework.boot.webmvc.autoconfigure.WebMvcAutoConfiguration")
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class AtlasWebMvcAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    ProblemDetailEnricher problemDetailEnricher(Clock clock, AtlasProperties properties, TraceIdProvider traceIdProvider) {
        return new ProblemDetailEnricher(clock, properties.web().problemTypeBaseUri(), traceIdProvider);
    }

    @Bean
    @ConditionalOnMissingBean(ResponseEntityExceptionHandler.class)
    ProblemDetailsExceptionHandler problemDetailsExceptionHandler(ProblemDetailEnricher enricher) {
        return new ProblemDetailsExceptionHandler(enricher);
    }

    @Bean
    WebMvcConfigurer requesterArgumentResolverConfigurer(RequesterResolver requesterResolver) {
        return new WebMvcConfigurer() {
            @Override
            public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
                resolvers.add(new RequesterArgumentResolver(requesterResolver));
            }
        };
    }
}
