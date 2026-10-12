package ma.atlasbank.customer.application.view;

import java.time.Instant;
import java.util.UUID;
import ma.atlasbank.customer.domain.CustomerStatus;

/**
 * Directory entry: enough to identify a customer, without personal details.
 */
public record CustomerSummaryView(UUID id, String fullName, String email, CustomerStatus status, Instant createdAt) {
}
