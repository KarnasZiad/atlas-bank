package ma.atlasbank.customer.application.command;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import ma.atlasbank.customer.domain.NationalId;

/**
 * Onboarding request filled by a bank employee after checking the customer's documents.
 *
 * @param preferredLanguage BCP 47 language tag; the bank default applies when omitted
 */
public record RegisterCustomerCommand(
        @NotBlank @Size(max = 100) String firstName,
        @NotBlank @Size(max = 100) String lastName,
        @NotBlank @Email @Size(max = 254) String email,
        @NotBlank @Size(max = 30) String phoneNumber,
        @NotNull @Past LocalDate dateOfBirth,
        @NotBlank @Pattern(regexp = NationalId.FORMAT, message = "is not a valid national identity number") String nationalId,
        @NotNull @Valid AddressPayload address,
        @Size(max = 8) String preferredLanguage) {
}
