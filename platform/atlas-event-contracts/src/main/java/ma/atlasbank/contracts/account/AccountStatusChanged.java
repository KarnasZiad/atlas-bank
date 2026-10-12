package ma.atlasbank.contracts.account;

import java.time.Instant;
import java.util.UUID;
import ma.atlasbank.contracts.IntegrationEvent;

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
