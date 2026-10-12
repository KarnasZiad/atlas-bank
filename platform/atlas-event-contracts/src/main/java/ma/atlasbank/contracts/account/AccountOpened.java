package ma.atlasbank.contracts.account;

import java.time.Instant;
import java.util.UUID;
import ma.atlasbank.contracts.IntegrationEvent;

public record AccountOpened(
        UUID eventId,
        Instant occurredAt,
        UUID accountId,
        String accountNumber,
        UUID customerId,
        AccountType accountType,
        String currency) implements IntegrationEvent {

    @Override
    public String partitionKey() {
        return accountNumber;
    }
}
