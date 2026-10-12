package ma.atlasbank.gateway.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Edge access rules ({@code atlas.gateway.*}).
 *
 * @param publicPaths      reachable without a token (probes, API console and descriptions)
 * @param protectedPaths   require a valid access token; everything else is denied
 */
@ConfigurationProperties("atlas.gateway")
public record EdgeSecurityProperties(
        @DefaultValue({"/actuator/health/**", "/actuator/info", "/actuator/prometheus",
                "/swagger-ui.html", "/swagger-ui/**", "/webjars/**", "/v3/api-docs/**", "/docs/**"})
        List<String> publicPaths,
        @DefaultValue("/api/**") List<String> protectedPaths) {
}
