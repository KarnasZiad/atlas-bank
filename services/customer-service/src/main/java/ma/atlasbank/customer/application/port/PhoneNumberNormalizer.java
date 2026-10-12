package ma.atlasbank.customer.application.port;

/**
 * Validates a phone number and returns it in E.164 format.
 */
public interface PhoneNumberNormalizer {

    String toE164(String phoneNumber);
}
