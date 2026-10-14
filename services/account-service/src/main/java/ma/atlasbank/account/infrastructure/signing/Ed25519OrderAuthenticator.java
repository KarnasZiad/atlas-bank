package ma.atlasbank.account.infrastructure.signing;

import java.security.PublicKey;
import java.util.Map;
import java.util.stream.Collectors;
import ma.atlasbank.account.application.port.OrderAuthenticator;
import ma.atlasbank.contracts.payment.OrderSignatures;
import ma.atlasbank.contracts.payment.TransactionRequested;
import org.springframework.stereotype.Component;

/**
 * Verifies the Ed25519 signature of orders with the scheme shared with the transaction service.
 */
@Component
class Ed25519OrderAuthenticator implements OrderAuthenticator {

    private final Map<String, PublicKey> trustedKeys;

    Ed25519OrderAuthenticator(OrderSigningProperties properties) {
        this.trustedKeys = properties.trustedKeys().entrySet().stream()
                .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, key -> OrderSignatures.publicKey(key.getValue())));
    }

    @Override
    public boolean isAuthentic(TransactionRequested order) {
        return OrderSignatures.isAuthentic(order, trustedKeys);
    }
}
