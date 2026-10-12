package ma.atlasbank.contracts.payment;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import ma.atlasbank.contracts.IntegrationEvent;

/**
 * A payment order accepted by the transaction service and awaiting posting by the ledger.
 *
 * <p>The ledger re-validates everything (ownership, status, funds, limits): it never trusts
 * an upstream decision about money.
 *
 * @param sourceAccountNumber debited account; {@code null} for a cash deposit
 * @param targetAccountNumber credited account; {@code null} for a cash withdrawal
 */
public record TransactionRequested(
        UUID eventId,
        Instant occurredAt,
        UUID transactionId,
        String reference,
        TransactionKind kind,
        String sourceAccountNumber,
        String targetAccountNumber,
        BigDecimal amount,
        String currency,
        String description,
        Initiator initiator) implements IntegrationEvent {

    /**
     * Keyed by the account whose balance decreases (or increases for deposits) so that all
     * movements of a given account are processed sequentially by the same consumer.
     */
    @Override
    public String partitionKey() {
        return sourceAccountNumber != null ? sourceAccountNumber : targetAccountNumber;
    }

    /**
     * @param customerId customer on whose behalf the order was placed; {@code null} when bank staff acted
     */
    public record Initiator(String subject, String username, UUID customerId, InitiatorType type) {
    }

    public enum InitiatorType {
        CUSTOMER,
        EMPLOYEE
    }
}
