package ma.atlasbank.customer.application.view;

import java.time.Instant;
import ma.atlasbank.customer.domain.CustomerStatus;

/**
 * One entry of a customer's audit trail.
 *
 * @param changeType INSERT, UPDATE or DELETE
 */
public record CustomerRevisionView(
        long revision,
        Instant changedAt,
        String changedBy,
        String changeType,
        CustomerStatus status,
        String statusReason,
        String email,
        String phoneNumber) {
}
