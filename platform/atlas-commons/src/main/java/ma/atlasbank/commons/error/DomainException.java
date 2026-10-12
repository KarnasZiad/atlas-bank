package ma.atlasbank.commons.error;

import java.util.Objects;

/**
 * Root of every business error. Subclasses express the error <em>category</em>
 * (not found, conflict, rule violation, forbidden); the {@link ErrorCode} carries the
 * precise reason. The web layer maps categories to HTTP statuses in a single place.
 */
public abstract class DomainException extends RuntimeException {

    private final transient ErrorCode errorCode;

    protected DomainException(ErrorCode errorCode, String messageTemplate, Object... arguments) {
        super(messageTemplate.formatted(arguments));
        this.errorCode = Objects.requireNonNull(errorCode, "errorCode must not be null");
    }

    public ErrorCode errorCode() {
        return errorCode;
    }
}
