package ma.atlasbank.customer.infrastructure.phone;

import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.google.i18n.phonenumbers.PhoneNumberUtil.PhoneNumberFormat;
import com.google.i18n.phonenumbers.Phonenumber.PhoneNumber;
import ma.atlasbank.commons.error.BusinessRuleViolationException;
import ma.atlasbank.customer.application.CustomerSettings;
import ma.atlasbank.customer.application.port.PhoneNumberNormalizer;
import ma.atlasbank.customer.domain.CustomerErrorCode;
import org.springframework.stereotype.Component;

/**
 * Phone validation delegated to Google's libphonenumber (numbering plans of every country),
 * national formats being interpreted in the bank's default region.
 */
@Component
class LibPhoneNumberNormalizer implements PhoneNumberNormalizer {

    private final PhoneNumberUtil phoneNumbers = PhoneNumberUtil.getInstance();
    private final String defaultRegion;

    LibPhoneNumberNormalizer(CustomerSettings settings) {
        this.defaultRegion = settings.defaultPhoneRegion();
    }

    @Override
    public String toE164(String phoneNumber) {
        try {
            PhoneNumber parsed = phoneNumbers.parse(phoneNumber, defaultRegion);
            if (!phoneNumbers.isValidNumber(parsed)) {
                throw invalid();
            }
            return phoneNumbers.format(parsed, PhoneNumberFormat.E164);
        } catch (NumberParseException unparsable) {
            throw invalid();
        }
    }

    private static BusinessRuleViolationException invalid() {
        return new BusinessRuleViolationException(CustomerErrorCode.INVALID_PHONE_NUMBER, "Invalid phone number");
    }
}
