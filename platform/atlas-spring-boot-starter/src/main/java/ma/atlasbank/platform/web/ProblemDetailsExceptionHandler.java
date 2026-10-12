package ma.atlasbank.platform.web;

import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import ma.atlasbank.commons.error.BusinessRuleViolationException;
import ma.atlasbank.commons.error.ConflictException;
import ma.atlasbank.commons.error.DomainException;
import ma.atlasbank.commons.error.ErrorCode;
import ma.atlasbank.commons.error.OperationNotPermittedException;
import ma.atlasbank.commons.error.ResourceNotFoundException;
import ma.atlasbank.commons.money.CurrencyMismatchException;
import ma.atlasbank.commons.money.InvalidMoneyException;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.data.core.PropertyReferenceException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Translates every exception into an RFC 9457 {@code application/problem+json} response.
 *
 * <p>Each problem carries a stable {@code code} for client logic and the {@code traceId}
 * so that support can correlate a customer complaint with the distributed trace. Internal
 * details (stack traces, SQL, class names) never leave the service.
 */
@RestControllerAdvice
public class ProblemDetailsExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger LOG = LoggerFactory.getLogger(ProblemDetailsExceptionHandler.class);

    private final ProblemDetailEnricher enricher;

    public ProblemDetailsExceptionHandler(ProblemDetailEnricher enricher) {
        this.enricher = enricher;
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    ProblemDetail handleNotFound(ResourceNotFoundException ex, HttpServletRequest request) {
        return domainProblem(HttpStatus.NOT_FOUND, ex, request);
    }

    @ExceptionHandler(ConflictException.class)
    ProblemDetail handleConflict(ConflictException ex, HttpServletRequest request) {
        return domainProblem(HttpStatus.CONFLICT, ex, request);
    }

    @ExceptionHandler(BusinessRuleViolationException.class)
    ProblemDetail handleRuleViolation(BusinessRuleViolationException ex, HttpServletRequest request) {
        return domainProblem(HttpStatus.UNPROCESSABLE_CONTENT, ex, request);
    }

    @ExceptionHandler(OperationNotPermittedException.class)
    ProblemDetail handleNotPermitted(OperationNotPermittedException ex, HttpServletRequest request) {
        return domainProblem(HttpStatus.FORBIDDEN, ex, request);
    }

    @ExceptionHandler(AccessDeniedException.class)
    ProblemDetail handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
        return problem(HttpStatus.FORBIDDEN, PlatformErrorCode.ACCESS_DENIED,
                "You are not allowed to perform this operation", request);
    }

    @ExceptionHandler({OptimisticLockingFailureException.class, PessimisticLockingFailureException.class})
    ProblemDetail handleConcurrentModification(RuntimeException ex, HttpServletRequest request) {
        LOG.info("Concurrent modification detected on {}: {}", request.getRequestURI(), ex.getMessage());
        return problem(HttpStatus.CONFLICT, PlatformErrorCode.CONCURRENT_MODIFICATION,
                "The resource was modified concurrently, please retry", request);
    }

    /**
     * A unique constraint fired: typically two concurrent requests creating the same resource.
     */
    @ExceptionHandler(DuplicateKeyException.class)
    ProblemDetail handleDuplicate(DuplicateKeyException ex, HttpServletRequest request) {
        LOG.info("Duplicate key on {}: {}", request.getRequestURI(), ex.getMostSpecificCause().getMessage());
        return problem(HttpStatus.CONFLICT, PlatformErrorCode.DUPLICATE_RESOURCE,
                "The resource already exists or is being created by a concurrent request", request);
    }

    @ExceptionHandler(PropertyReferenceException.class)
    ProblemDetail handleInvalidSort(PropertyReferenceException ex, HttpServletRequest request) {
        return problem(HttpStatus.BAD_REQUEST, PlatformErrorCode.INVALID_SORT_PROPERTY,
                "Unknown sort property '%s'".formatted(ex.getPropertyName()), request);
    }

    @ExceptionHandler(InvalidMoneyException.class)
    ProblemDetail handleInvalidAmount(InvalidMoneyException ex, HttpServletRequest request) {
        return problem(HttpStatus.BAD_REQUEST, PlatformErrorCode.INVALID_AMOUNT, ex.getMessage(), request);
    }

    @ExceptionHandler(CurrencyMismatchException.class)
    ProblemDetail handleCurrencyMismatch(CurrencyMismatchException ex, HttpServletRequest request) {
        return problem(HttpStatus.UNPROCESSABLE_CONTENT, PlatformErrorCode.CURRENCY_MISMATCH, ex.getMessage(), request);
    }

    @ExceptionHandler(Exception.class)
    ProblemDetail handleUnexpected(Exception ex, HttpServletRequest request) {
        LOG.error("Unexpected error while processing {} {}", request.getMethod(), request.getRequestURI(), ex);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, PlatformErrorCode.INTERNAL_ERROR,
                "An unexpected error occurred. Please contact support with the trace identifier.", request);
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        ProblemDetail body = ex.getBody();
        body.setDetail("The request contains invalid fields");
        body.setProperty("errors", ex.getBindingResult().getFieldErrors().stream().map(this::fieldViolation).toList());
        return handleExceptionInternal(ex, body, headers, status, request);
    }

    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(
            HandlerMethodValidationException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        ProblemDetail body = ex.getBody();
        body.setDetail("The request contains invalid parameters");
        List<Map<String, String>> errors = ex.getParameterValidationResults().stream()
                .flatMap(result -> result.getResolvableErrors().stream()
                        .map(error -> Map.of(
                                "field", result.getMethodParameter().getParameterName() == null
                                        ? "parameter" : result.getMethodParameter().getParameterName(),
                                "message", String.valueOf(error.getDefaultMessage()))))
                .toList();
        body.setProperty("errors", errors);
        return handleExceptionInternal(ex, body, headers, status, request);
    }

    /**
     * Single exit point for the exceptions handled by the Spring MVC base class: they get the
     * same enrichment ({@code code}, {@code traceId}, {@code timestamp}) as business errors.
     */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception ex, @Nullable Object body, HttpHeaders headers, HttpStatusCode statusCode, WebRequest request) {
        if (body instanceof ProblemDetail problem && request instanceof ServletWebRequest servletRequest) {
            enricher.enrich(problem, frameworkErrorCode(ex, statusCode), servletRequest.getRequest());
        }
        return super.handleExceptionInternal(ex, body, headers, statusCode, request);
    }

    private static ErrorCode frameworkErrorCode(Exception ex, HttpStatusCode status) {
        if (ex instanceof MethodArgumentNotValidException || ex instanceof HandlerMethodValidationException) {
            return PlatformErrorCode.VALIDATION_FAILED;
        }
        if (status.value() == HttpStatus.BAD_REQUEST.value()) {
            return PlatformErrorCode.MALFORMED_REQUEST;
        }
        return HttpStatusErrorCode.of(status);
    }

    private Map<String, String> fieldViolation(FieldError error) {
        return Map.of(
                "field", error.getField(),
                "message", error.getDefaultMessage() == null ? "is invalid" : error.getDefaultMessage());
    }

    private ProblemDetail domainProblem(HttpStatus status, DomainException ex, HttpServletRequest request) {
        return problem(status, ex.errorCode(), ex.getMessage(), request);
    }

    private ProblemDetail problem(HttpStatus status, ErrorCode code, String detail, HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(status.getReasonPhrase());
        problem.setInstance(URI.create(request.getRequestURI()));
        return enricher.enrich(problem, code, request);
    }

    /**
     * Error code derived from an HTTP status for framework errors (405, 406, 415...).
     */
    private record HttpStatusErrorCode(String name) implements ErrorCode {

        static ErrorCode of(HttpStatusCode status) {
            HttpStatus resolved = HttpStatus.resolve(status.value());
            return new HttpStatusErrorCode(resolved == null ? "HTTP_" + status.value() : resolved.name().toUpperCase(Locale.ROOT));
        }
    }
}
