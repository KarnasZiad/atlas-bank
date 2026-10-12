package ma.atlasbank.customer.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.util.Locale;
import java.util.Set;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import ma.atlasbank.commons.error.BusinessRuleViolationException;

/**
 * Postal address (value object). {@code country} is an ISO 3166-1 alpha-2 code.
 */
@Getter
@Embeddable
@EqualsAndHashCode
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Address {

    @Column(name = "address_line", nullable = false, length = 200)
    private String line;

    @Column(name = "address_city", nullable = false, length = 100)
    private String city;

    @Column(name = "address_postal_code", nullable = false, length = 20)
    private String postalCode;

    @Column(name = "address_country", nullable = false, length = 2)
    private String country;

    private static final Set<String> ISO_COUNTRIES = Locale.getISOCountries(Locale.IsoCountryCode.PART1_ALPHA2);

    public Address(String line, String city, String postalCode, String country) {
        this.line = line.strip();
        this.city = city.strip();
        this.postalCode = postalCode.strip();
        this.country = country.strip().toUpperCase(Locale.ROOT);
        if (!ISO_COUNTRIES.contains(this.country)) {
            throw new BusinessRuleViolationException(CustomerErrorCode.INVALID_COUNTRY_CODE,
                    "'%s' is not an ISO 3166-1 alpha-2 country code", country);
        }
    }
}
