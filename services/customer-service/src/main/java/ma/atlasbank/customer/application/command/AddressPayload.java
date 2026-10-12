package ma.atlasbank.customer.application.command;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import ma.atlasbank.customer.domain.Address;

public record AddressPayload(
        @NotBlank @Size(max = 200) String line,
        @NotBlank @Size(max = 100) String city,
        @NotBlank @Size(max = 20) String postalCode,
        @NotBlank @Pattern(regexp = "^[A-Za-z]{2}$", message = "must be an ISO 3166-1 alpha-2 country code") String country) {

    public Address toAddress() {
        return new Address(line, city, postalCode, country);
    }
}
