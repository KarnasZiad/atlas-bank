package ma.atlasbank.gateway.config;

import java.net.InetSocketAddress;
import java.security.Principal;
import java.util.Optional;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.cloud.gateway.support.ipresolver.RemoteAddressResolver;
import org.springframework.cloud.gateway.support.ipresolver.XForwardedRemoteAddressResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;
import reactor.core.publisher.Mono;

/**
 * First line of defence: only well-formed, signed, unexpired tokens issued for this API reach the
 * services (which validate them again: zero trust), unknown paths are denied, and every caller is
 * rate limited.
 */
@Configuration(proxyBeanMethods = false)
@EnableWebFluxSecurity
class EdgeSecurityConfiguration {

    @Bean
    SecurityWebFilterChain edgeSecurityFilterChain(ServerHttpSecurity http, EdgeSecurityProperties properties) {
        return http
                // Bearer-token API: no session, no cookie, hence no CSRF exposure.
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
                .logout(ServerHttpSecurity.LogoutSpec::disable)
                .authorizeExchange(exchanges -> exchanges
                        .pathMatchers(properties.publicPaths().toArray(String[]::new)).permitAll()
                        .pathMatchers(HttpMethod.OPTIONS, properties.protectedPaths().toArray(String[]::new)).permitAll()
                        .pathMatchers(properties.protectedPaths().toArray(String[]::new)).authenticated()
                        .anyExchange().denyAll())
                .oauth2ResourceServer(resourceServer -> resourceServer.jwt(Customizer.withDefaults()))
                .build();
    }

    /**
     * Rate-limit buckets: one per authenticated user, otherwise one per client address. Behind a load
     * balancer every connection comes from the balancer, so the client address is taken from
     * {@code X-Forwarded-For}, trusting only the hops added by our own proxies.
     *
     * <p>When Redis is unreachable the limiter lets requests through (availability first): authentication
     * still applies, and Keycloak keeps its own brute-force protection.
     */
    @Bean
    KeyResolver callerKeyResolver(EdgeSecurityProperties properties) {
        RemoteAddressResolver clients = properties.trustedProxies() > 0
                ? XForwardedRemoteAddressResolver.maxTrustedIndex(properties.trustedProxies())
                : new RemoteAddressResolver() {
                };
        return exchange -> exchange.getPrincipal()
                .map(Principal::getName)
                .map(name -> "user:" + name)
                .switchIfEmpty(Mono.fromSupplier(() -> "ip:" + Optional.ofNullable(clients.resolve(exchange))
                        .map(InetSocketAddress::getHostString)
                        .orElse("unknown")));
    }
}
