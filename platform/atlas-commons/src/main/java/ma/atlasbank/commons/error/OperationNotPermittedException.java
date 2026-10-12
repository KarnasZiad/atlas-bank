package ma.atlasbank.commons.error;

/**
 * The requester is authenticated but the operation is not allowed for them
 * (for example the maker-checker rule forbids approving one's own request).
 */
public class OperationNotPermittedException extends DomainException {

    public OperationNotPermittedException(ErrorCode errorCode, String messageTemplate, Object... arguments) {
        super(errorCode, messageTemplate, arguments);
    }
}
