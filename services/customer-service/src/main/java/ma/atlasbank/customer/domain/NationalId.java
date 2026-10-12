package ma.atlasbank.customer.domain;

import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;
import ma.atlasbank.commons.error.BusinessRuleViolationException;

/**
 * Moroccan national identity card number (CNIE): one or two letters followed by digits.
 *
 * <p>Sensitive personal data: it is encrypted at rest, looked up through a keyed fingerprint
 * and only ever displayed masked.
 */
public record NationalId(String value) {

    /** Shared with the API validation so that both layers accept exactly the same inputs. */
    public static final String FORMAT = "^[A-Za-z]{1,2}\\d{1,7}$";

    private static final Pattern PATTERN = Pattern.compile(FORMAT);
    private static final int VISIBLE_SUFFIX_LENGTH = 2;

    public NationalId {
        Objects.requireNonNull(value, "value must not be null");
        value = value.strip().toUpperCase(Locale.ROOT);
        if (!PATTERN.matcher(value).matches()) {
            throw new BusinessRuleViolationException(CustomerErrorCode.INVALID_NATIONAL_ID, "Invalid national identity number");
        }
    }

    public String masked() {
        int hidden = value.length() - VISIBLE_SUFFIX_LENGTH;
        return "*".repeat(hidden) + value.substring(hidden);
    }

    /**
     * Never leak the clear value through logs or exception messages.
     */
    @Override
    public String toString() {
        return masked();
    }
}
