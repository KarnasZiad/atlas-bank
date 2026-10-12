package ma.atlasbank.contracts.ledger;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import ma.atlasbank.contracts.IntegrationEvent;
import ma.atlasbank.contracts.payment.TransactionKind;

/**
 * Result of posting a {@link ma.atlasbank.contracts.payment.TransactionRequested} to the ledger.
 *
 * @param rejectionCode stable business reason when {@code result} is {@code REJECTED}, otherwise {@code null}
 * @param debited       party whose account was (or would have been) debited; {@code null} for deposits
 * @param credited      party whose account was (or would have been) credited; {@code null} for withdrawals
 * @param initiatorCustomerId customer who placed the order, {@code null} when bank staff acted
 */
public record TransactionOutcome(
        UUID eventId,
        Instant occurredAt,
        UUID transactionId,
        String reference,
        TransactionKind kind,
        PostingResult result,
        String rejectionCode,
        BigDecimal amount,
        String currency,
        Party debited,
        Party credited,
        UUID initiatorCustomerId) implements IntegrationEvent {

    @Override
    public String partitionKey() {
        return transactionId.toString();
    }

    /**
     * @param customerId owner of the account; {@code null} if the account is unknown to the ledger
     */
    public record Party(String accountNumber, UUID customerId) {
    }

    public enum PostingResult {
        POSTED,
        REJECTED
    }
}
