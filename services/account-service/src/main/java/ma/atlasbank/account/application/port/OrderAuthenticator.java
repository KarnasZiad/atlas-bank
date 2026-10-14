package ma.atlasbank.account.application.port;

import ma.atlasbank.contracts.payment.TransactionRequested;

/**
 * Tells payment orders signed by the transaction service from orders written to the topic by anyone
 * else: the ledger acts on the initiator and the approval an order carries, so they must be genuine.
 */
public interface OrderAuthenticator {

    boolean isAuthentic(TransactionRequested order);
}
