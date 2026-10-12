package ma.atlasbank.transaction.application.command;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record TransferCommand(
        @NotBlank @Size(max = 34) String sourceAccountNumber,
        @NotBlank @Size(max = 34) String targetAccountNumber,
        @NotNull @Positive @Digits(integer = 15, fraction = 4) BigDecimal amount,
        @NotNull @Pattern(regexp = "^[A-Z]{3}$", message = "must be an ISO 4217 currency code") String currency,
        @Size(max = 140) String description) {
}
