package ma.atlasbank.customer.domain;

import java.util.Locale;
import java.util.Objects;

/**
 * How to reach the customer. The phone number is expected in E.164 format.
 */
public record ContactDetails(String email, String phoneNumber, Address address) {

    public ContactDetails {
        email = Objects.requireNonNull(email, "email").strip().toLowerCase(Locale.ROOT);
        Objects.requireNonNull(phoneNumber, "phoneNumber");
        Objects.requireNonNull(address, "address");
    }
}
