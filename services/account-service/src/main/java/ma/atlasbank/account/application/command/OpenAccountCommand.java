package ma.atlasbank.account.application.command;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.util.UUID;
import ma.atlasbank.account.domain.AccountType;

/**
 * @param currency ISO 4217 code; must be offered for the requested product
 */
public record OpenAccountCommand(
        @NotNull UUID customerId,
        @NotNull AccountType type,
        @NotNull @Pattern(regexp = "^[A-Z]{3}$", message = "must be an ISO 4217 currency code") String currency) {
}
