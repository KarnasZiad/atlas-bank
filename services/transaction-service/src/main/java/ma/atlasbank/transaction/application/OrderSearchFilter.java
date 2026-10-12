package ma.atlasbank.transaction.application;

import java.time.Instant;
import java.util.UUID;
import ma.atlasbank.transaction.domain.OrderKind;
import ma.atlasbank.transaction.domain.OrderStatus;

/**
 * Resolved search filter: business dates already converted to instants, visibility applied.
 *
 * @param initiatorCustomerId restricts the result to the orders of one customer (customer channel)
 * @param to                  exclusive upper bound
 */
public record OrderSearchFilter(OrderStatus status, OrderKind kind, String accountNumber, Instant from, Instant to,
                                UUID initiatorCustomerId) {
}
