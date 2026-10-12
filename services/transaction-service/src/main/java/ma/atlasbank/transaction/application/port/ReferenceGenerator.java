package ma.atlasbank.transaction.application.port;

import ma.atlasbank.transaction.domain.OrderKind;

/**
 * Human-readable, unique reference quoted to customers and support (statements, receipts).
 */
public interface ReferenceGenerator {

    String nextReference(OrderKind kind);
}
