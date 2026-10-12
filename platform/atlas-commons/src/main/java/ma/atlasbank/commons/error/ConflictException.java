package ma.atlasbank.commons.error;

/**
 * The request conflicts with the current state of a resource (duplicate, concurrent change...).
 */
public class ConflictException extends DomainException {

    public ConflictException(ErrorCode errorCode, String messageTemplate, Object... arguments) {
        super(errorCode, messageTemplate, arguments);
    }
}
