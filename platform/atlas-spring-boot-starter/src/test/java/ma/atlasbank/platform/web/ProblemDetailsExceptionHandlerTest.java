package ma.atlasbank.platform.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.BatchUpdateException;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;

class ProblemDetailsExceptionHandlerTest {

    private final ProblemDetailsExceptionHandler handler = new ProblemDetailsExceptionHandler(new ProblemDetailEnricher(
            Clock.fixed(Instant.parse("2026-10-13T10:00:00Z"), ZoneOffset.UTC), "urn:atlasbank:problem:", Optional::empty));

    private final MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/things");

    @Test
    void reportsAUniqueViolationRaisedThroughJpaAsAConflict() {
        // What Spring's Hibernate translator produces: a plain integrity violation wrapping the driver error.
        var violation = new DataIntegrityViolationException("could not execute statement",
                new RuntimeException("constraint [uk_thing]", new SQLException("duplicate key value", "23505")));

        ProblemDetail problem = handler.handleIntegrityViolation(violation, request);

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.CONFLICT.value());
        assertThat(problem.getProperties()).containsEntry("code", "DUPLICATE_RESOURCE");
    }

    @Test
    void findsTheUniqueViolationInsideABatchFailure() {
        BatchUpdateException batch = new BatchUpdateException("batch failed", "23000", new int[0]);
        batch.setNextException(new SQLException("duplicate key value", "23505"));

        ProblemDetail problem = handler.handleIntegrityViolation(new DataIntegrityViolationException("batch", batch), request);

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.CONFLICT.value());
    }

    @Test
    void keepsOtherIntegrityViolationsAsUnexpectedErrors() {
        var checkViolation = new DataIntegrityViolationException("could not execute statement",
                new SQLException("violates check constraint", "23514"));

        ProblemDetail problem = handler.handleIntegrityViolation(checkViolation, request);

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR.value());
        assertThat(problem.getProperties()).containsEntry("code", "INTERNAL_ERROR");
        assertThat(problem.getDetail()).doesNotContain("check constraint");
    }

    @Test
    void answersAnUnusableTokenWith401() {
        ResponseEntity<ProblemDetail> response = handler.handleAuthentication(
                new InvalidBearerTokenException("Claim 'customer_id' is not a valid identifier"), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getHeaders().getFirst(HttpHeaders.WWW_AUTHENTICATE)).contains("invalid_token");
        assertThat(response.getBody().getProperties()).containsEntry("code", "INVALID_TOKEN");
    }
}
