package ma.atlasbank.contracts.payment;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import ma.atlasbank.contracts.IntegrationEvent;

/**
 * A payment order accepted by the transaction service and awaiting posting by the ledger.
 *
 * <p>The ledger re-validates everything (ownership, status, funds, limits): it never trusts
 * an upstream decision about money. It does trust <em>who</em> placed the order, which is why the
 * order is signed by the transaction service ({@link OrderSignatures}) and an unsigned or altered
 * order is never posted.
 *
 * @param sourceAccountNumber debited account; {@code null} for a cash deposit
 * @param targetAccountNumber credited account; {@code null} for a cash withdrawal
 * @param approval            second employee who approved a staff order (four-eyes control); {@code null}
 *                            when the order did not need one
 * @param signature           signature of the transaction service over {@link #signedContent()}
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
        Initiator initiator,
        Approval approval,
        OrderSignature signature) implements IntegrationEvent {

    private static final String SIGNED_CONTENT_VERSION = "atlas-order-v1";

    /**
     * Keyed by the account whose balance decreases (or increases for deposits) so that all
     * movements of a given account are processed sequentially by the same consumer.
     */
    @Override
    public String partitionKey() {
        return sourceAccountNumber != null ? sourceAccountNumber : targetAccountNumber;
    }

    public TransactionRequested withSignature(OrderSignature newSignature) {
        return new TransactionRequested(eventId, occurredAt, transactionId, reference, kind, sourceAccountNumber,
                targetAccountNumber, amount, currency, description, initiator, approval, newSignature);
    }

    /**
     * Everything that decides what the ledger does, in an unambiguous encoding (each value is length
     * prefixed, so no field can bleed into its neighbour). Event metadata is left out: a replayed
     * order is harmless, the ledger posts each transaction once.
     */
    public String signedContent() {
        StringBuilder content = new StringBuilder(SIGNED_CONTENT_VERSION).append(';');
        Initiator by = initiator == null ? new Initiator(null, null, null, null) : initiator;
        for (Object value : new Object[] {
                transactionId, reference, kind, sourceAccountNumber, targetAccountNumber,
                amount == null ? null : amount.stripTrailingZeros().toPlainString(), currency, description,
                by.subject(), by.username(), by.customerId(), by.type(),
                approval == null ? null : approval.subject(), approval == null ? null : approval.username()}) {
            if (value == null) {
                content.append("~;");
            } else {
                String text = value.toString();
                content.append(text.length()).append(':').append(text).append(';');
            }
        }
        return content.toString();
    }

    /**
     * @param customerId customer on whose behalf the order was placed; {@code null} when bank staff acted
     */
    public record Initiator(String subject, String username, UUID customerId, InitiatorType type) {
    }

    /**
     * @param subject immutable identifier of the approving employee at the identity provider
     */
    public record Approval(String subject, String username) {
    }

    public enum InitiatorType {
        CUSTOMER,
        EMPLOYEE
    }
}
