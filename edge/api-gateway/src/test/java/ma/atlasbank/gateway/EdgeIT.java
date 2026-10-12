package ma.atlasbank.gateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.mockJwt;

import com.redis.testcontainers.RedisContainer;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.JwtMutator;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.testcontainers.utility.DockerImageName;

/**
 * Edge behaviour with a real Redis (rate limiting) and unreachable downstream services
 * (circuit-breaker fallback).
 */
@SpringBootTest(properties = {
        "ATLAS_RATE_LIMIT_PER_SECOND=1",
        "ATLAS_RATE_LIMIT_BURST=3",
        "ATLAS_ACCOUNT_SERVICE_URL=http://localhost:1"
})
@AutoConfigureWebTestClient
@Import(EdgeIT.Redis.class)
class EdgeIT {

    @Autowired
    private WebTestClient client;

    @Test
    void rejectsCallsWithoutAToken() {
        client.get().uri("/api/v1/accounts").exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void deniesPathsThatAreNotPartOfThePublicApi() {
        client.mutateWith(mockJwt()).get().uri("/internal/anything").exchange()
                .expectStatus().isForbidden();
    }

    @Test
    void answersWithAProblemWhenAServiceIsUnavailable() {
        client.mutateWith(caller()).get().uri("/api/v1/accounts").exchange()
                .expectStatus().isEqualTo(HttpStatus.SERVICE_UNAVAILABLE)
                .expectHeader().contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .expectHeader().exists("Retry-After")
                .expectBody().jsonPath("$.code").isEqualTo("SERVICE_UNAVAILABLE");
    }

    @Test
    void addsSecurityHeaders() {
        client.mutateWith(caller()).get().uri("/api/v1/accounts").exchange()
                .expectHeader().valueEquals("X-Content-Type-Options", "nosniff")
                .expectHeader().valueEquals("X-Frame-Options", "DENY");
    }

    @Test
    void rateLimitsEachCallerIndependently() {
        var greedy = caller();
        List<HttpStatusCode> burst = IntStream.range(0, 10)
                .mapToObj(i -> client.mutateWith(greedy).get().uri("/api/v1/accounts").exchange()
                        .returnResult(Void.class).getStatus())
                .toList();

        assertThat(burst).contains(HttpStatus.TOO_MANY_REQUESTS);
        client.mutateWith(caller()).get().uri("/api/v1/accounts").exchange()
                .expectStatus().isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    }

    @Test
    void exposesProbesWithoutAuthentication() {
        client.get().uri("/actuator/health/liveness").exchange()
                .expectStatus().isOk();
    }

    private static JwtMutator caller() {
        return mockJwt().jwt(jwt -> jwt.subject("user-" + UUID.randomUUID()));
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class Redis {

        @Bean
        @ServiceConnection
        RedisContainer redis() {
            return new RedisContainer(DockerImageName.parse("redis:8.2-alpine"));
        }
    }
}
