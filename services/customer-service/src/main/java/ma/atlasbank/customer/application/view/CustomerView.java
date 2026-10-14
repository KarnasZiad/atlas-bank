package ma.atlasbank.customer.application.view;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import ma.atlasbank.customer.domain.CustomerStatus;

/**
 * Full customer profile. The national identity number is always masked.
 *
 * @param statusReason internal note of the bank, shown to staff only
 * @param registeredBy employee who registered the file, shown to staff only
 * @param verifiedBy   employee who approved the file, shown to staff only
 */
public record CustomerView(
        UUID id,
        String firstName,
        String lastName,
        String email,
        String phoneNumber,
        LocalDate dateOfBirth,
        String nationalId,
        AddressView address,
        String preferredLanguage,
        CustomerStatus status,
        String statusReason,
        boolean identityProvisioned,
        String registeredBy,
        String verifiedBy,
        Instant verifiedAt,
        Instant createdAt,
        Instant lastModifiedAt) {
}
