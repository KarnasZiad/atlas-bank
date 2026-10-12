package ma.atlasbank.transaction.application.view;

/**
 * Result of an order intake.
 *
 * @param replayed {@code true} when the idempotency key matched an earlier identical request:
 *                 the original order is returned and nothing new was created
 */
public record OrderSubmission(PaymentOrderView order, boolean replayed) {
}
