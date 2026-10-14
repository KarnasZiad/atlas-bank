package ma.atlasbank.contracts.account;

import java.time.Instant;
import java.util.UUID;
import ma.atlasbank.contracts.IntegrationEvent;

/**
 * @param reason internal note written by the bank (it may refer to an investigation): consumers must
 *               never show it to the customer
 */
public record AccountStatusChanged(
        UUID eventId,
        Instant occurredAt,
        UUID accountId,
        String accountNumber,
        UUID customerId,
        AccountStatus previousStatus,
        AccountStatus status,
        String reason) implements IntegrationEvent {

    @Override
    public String partitionKey() {
        return accountNumber;
    }
}
