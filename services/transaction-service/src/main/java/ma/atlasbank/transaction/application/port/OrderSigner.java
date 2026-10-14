package ma.atlasbank.transaction.application.port;

import ma.atlasbank.contracts.payment.TransactionRequested;

/**
 * Signs accepted orders so that the ledger can tell them from orders written to the topic by anyone else.
 */
public interface OrderSigner {

    TransactionRequested sign(TransactionRequested order);
}
