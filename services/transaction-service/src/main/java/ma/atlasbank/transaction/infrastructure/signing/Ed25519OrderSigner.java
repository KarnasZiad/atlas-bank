package ma.atlasbank.transaction.infrastructure.signing;

import java.security.PrivateKey;
import ma.atlasbank.contracts.payment.OrderSignatures;
import ma.atlasbank.contracts.payment.TransactionRequested;
import ma.atlasbank.transaction.application.port.OrderSigner;
import org.springframework.stereotype.Component;

/**
 * Ed25519 signature of orders, with the scheme shared with the ledger ({@link OrderSignatures}).
 */
@Component
class Ed25519OrderSigner implements OrderSigner {

    private final String keyId;
    private final PrivateKey key;

    Ed25519OrderSigner(OrderSigningProperties properties) {
        this.keyId = properties.keyId();
        this.key = OrderSignatures.privateKey(properties.privateKey());
    }

    @Override
    public TransactionRequested sign(TransactionRequested order) {
        return OrderSignatures.sign(order, keyId, key);
    }
}
