package ma.atlasbank.customer.application;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.Set;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Onboarding policy ({@code atlas.customers.*}).
 *
 * @param minimumAge         legal age required to open a relationship
 * @param defaultPhoneRegion ISO region used to interpret national-format phone numbers
 * @param defaultLanguage    language used when the customer expresses no preference
 * @param supportedLanguages languages in which the bank communicates
 */
@Validated
@ConfigurationProperties("atlas.customers")
public record CustomerSettings(
        @DefaultValue("18") @Min(16) int minimumAge,
        @DefaultValue("MA") @NotBlank String defaultPhoneRegion,
        @DefaultValue("fr") @NotBlank String defaultLanguage,
        @DefaultValue({"fr", "en", "ar"}) @NotEmpty Set<String> supportedLanguages) {
}
