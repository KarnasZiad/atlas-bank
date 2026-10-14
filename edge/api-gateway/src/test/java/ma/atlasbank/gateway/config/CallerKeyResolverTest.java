package ma.atlasbank.gateway.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.InetSocketAddress;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;

class CallerKeyResolverTest {

    private static final InetSocketAddress LOAD_BALANCER = new InetSocketAddress("10.0.0.5", 41000);

    @Test
    void usesTheConnectionAddressWhenClientsConnectDirectly() {
        assertThat(keyOf(resolver(0), request("198.51.100.9"))).isEqualTo("ip:10.0.0.5");
    }

    @Test
    void readsTheClientAddressAddedByOurOwnProxy() {
        assertThat(keyOf(resolver(1), request("203.0.113.7"))).isEqualTo("ip:203.0.113.7");
    }

    @Test
    void ignoresAddressesTheClientWroteItself() {
        // The client sends a forged X-Forwarded-For; our load balancer appends the real address.
        assertThat(keyOf(resolver(1), request("1.2.3.4, 203.0.113.7"))).isEqualTo("ip:203.0.113.7");
    }

    private static KeyResolver resolver(int trustedProxies) {
        return new EdgeSecurityConfiguration().callerKeyResolver(
                new EdgeSecurityProperties(List.of(), List.of("/api/**"), trustedProxies));
    }

    private static MockServerWebExchange request(String forwardedFor) {
        return MockServerWebExchange.from(MockServerHttpRequest.get("/docs/customer-service")
                .remoteAddress(LOAD_BALANCER)
                .header("X-Forwarded-For", forwardedFor));
    }

    private static String keyOf(KeyResolver resolver, MockServerWebExchange exchange) {
        return resolver.resolve(exchange).block();
    }
}
