package ma.atlasbank.commons.error;

/**
 * The requested resource does not exist <em>or</em> is not visible to the requester.
 * Both cases are deliberately indistinguishable to prevent resource enumeration.
 */
public class ResourceNotFoundException extends DomainException {

    public ResourceNotFoundException(ErrorCode errorCode, String messageTemplate, Object... arguments) {
        super(errorCode, messageTemplate, arguments);
    }
}
