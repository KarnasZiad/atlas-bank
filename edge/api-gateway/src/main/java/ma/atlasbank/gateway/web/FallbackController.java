package ma.atlasbank.gateway.web;

import java.net.URI;
import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;

/**
 * Target of the circuit breakers: a downstream service is unreachable or too slow, so the gateway
 * answers immediately with an RFC 9457 problem instead of letting clients hang.
 */
@RestController
class FallbackController {

    static final String UNAVAILABLE = "/fallback/unavailable";

    @RequestMapping(UNAVAILABLE)
    ResponseEntity<ProblemDetail> serviceUnavailable(ServerWebExchange exchange) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE,
                "The service is temporarily unavailable, please retry later. Payment orders can be retried "
                        + "safely with the same Idempotency-Key.");
        problem.setTitle(HttpStatus.SERVICE_UNAVAILABLE.getReasonPhrase());
        problem.setType(URI.create("urn:atlasbank:problem:service-unavailable"));
        problem.setProperty("code", "SERVICE_UNAVAILABLE");
        problem.setProperty("timestamp", Instant.now());
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .header("Retry-After", "5")
                .body(problem);
    }
}
