package ma.atlasbank.customer.domain;

import java.time.LocalDate;
import java.time.Period;
import java.util.Locale;
import java.util.Objects;

/**
 * Identity attributes captured at onboarding. They do not change during the relationship.
 */
public record PersonalDetails(
        String firstName,
        String lastName,
        LocalDate dateOfBirth,
        NationalId nationalId,
        Locale preferredLanguage) {

    public PersonalDetails {
        firstName = Objects.requireNonNull(firstName, "firstName").strip();
        lastName = Objects.requireNonNull(lastName, "lastName").strip();
        Objects.requireNonNull(dateOfBirth, "dateOfBirth");
        Objects.requireNonNull(nationalId, "nationalId");
        Objects.requireNonNull(preferredLanguage, "preferredLanguage");
    }

    public int ageOn(LocalDate date) {
        return Period.between(dateOfBirth, date).getYears();
    }
}
