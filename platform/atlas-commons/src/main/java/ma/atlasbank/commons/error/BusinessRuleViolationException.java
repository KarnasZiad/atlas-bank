package ma.atlasbank.commons.error;

/**
 * The request is well-formed but breaks a business rule (insufficient funds, frozen account...).
 */
public class BusinessRuleViolationException extends DomainException {

    public BusinessRuleViolationException(ErrorCode errorCode, String messageTemplate, Object... arguments) {
        super(errorCode, messageTemplate, arguments);
    }
}
