package ma.atlasbank.account.infrastructure.numbering;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import org.iban4j.CountryCode;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Identity of the bank inside the national account numbering scheme.
 *
 * @param countryCode country of the IBANs issued
 * @param bankCode    code of the bank (assigned by the central bank)
 * @param branchCode  code of the branch holding the accounts
 */
@Validated
@ConfigurationProperties("atlas.accounts.numbering")
public record AccountNumberingProperties(
        @NotNull CountryCode countryCode,
        @NotBlank @Pattern(regexp = "^\\d+$") String bankCode,
        @NotBlank @Pattern(regexp = "^\\d+$") String branchCode) {
}
