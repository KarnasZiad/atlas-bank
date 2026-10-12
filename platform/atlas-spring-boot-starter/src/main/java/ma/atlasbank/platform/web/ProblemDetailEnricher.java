package ma.atlasbank.platform.web;

import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import ma.atlasbank.commons.error.ErrorCode;
import ma.atlasbank.platform.observability.TraceIdProvider;
import org.springframework.http.ProblemDetail;

/**
 * Adds the platform-wide extension members to a problem: {@code code}, {@code timestamp}
 * and {@code traceId}, and derives the problem {@code type} URI from the error code.
 */
public class ProblemDetailEnricher {

    private final Clock clock;
    private final String typeBaseUri;
    private final TraceIdProvider traceIdProvider;

    public ProblemDetailEnricher(Clock clock, String typeBaseUri, TraceIdProvider traceIdProvider) {
        this.clock = clock;
        this.typeBaseUri = typeBaseUri;
        this.traceIdProvider = traceIdProvider;
    }

    public ProblemDetail enrich(ProblemDetail problem, ErrorCode code, HttpServletRequest request) {
        problem.setType(URI.create(typeBaseUri + code.code().toLowerCase(Locale.ROOT).replace('_', '-')));
        if (problem.getInstance() == null) {
            problem.setInstance(URI.create(request.getRequestURI()));
        }
        problem.setProperty("code", code.code());
        problem.setProperty("timestamp", Instant.now(clock));
        traceIdProvider.currentTraceId().ifPresent(traceId -> problem.setProperty("traceId", traceId));
        return problem;
    }
}
