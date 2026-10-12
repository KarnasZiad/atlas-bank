package ma.atlasbank.customer.application.command;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpdateContactCommand(
        @NotBlank @Size(max = 30) String phoneNumber,
        @NotNull @Valid AddressPayload address) {
}
