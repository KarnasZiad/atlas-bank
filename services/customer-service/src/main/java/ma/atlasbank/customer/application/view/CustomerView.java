package ma.atlasbank.customer.application.view;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import ma.atlasbank.customer.domain.CustomerStatus;

/**
 * Full customer profile. The national identity number is always masked.
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
