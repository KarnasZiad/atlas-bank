package ma.atlasbank.gateway.config;

import jakarta.validation.constraints.PositiveOrZero;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Edge access rules ({@code atlas.gateway.*}).
 *
 * <p>Metrics and build information are not part of the public surface: the gateway faces the
 * Internet, and they describe the internals (routes, versions, error rates). They are exported to the
 * observability stack (OTLP) or read from the internal network.
 *
 * @param publicPaths    reachable without a token (probes, API console and descriptions)
 * @param protectedPaths require a valid access token; everything else is denied
 * @param trustedProxies number of reverse proxies (load balancer, ingress) in front of the gateway:
 *                       the client address is read that many hops back in {@code X-Forwarded-For}.
 *                       0 when clients connect directly; never more than the proxies you control,
 *                       or a client could choose its own rate-limit bucket.
 */
@Validated
@ConfigurationProperties("atlas.gateway")
public record EdgeSecurityProperties(
        @DefaultValue({"/actuator/health/**", "/swagger-ui.html", "/swagger-ui/**", "/webjars/**", "/v3/api-docs/**",
                "/docs/**"})
        List<String> publicPaths,
        @DefaultValue("/api/**") List<String> protectedPaths,
        @DefaultValue("0") @PositiveOrZero int trustedProxies) {
}
