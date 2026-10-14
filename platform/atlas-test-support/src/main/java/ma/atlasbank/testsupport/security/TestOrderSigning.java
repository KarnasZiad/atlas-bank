package ma.atlasbank.testsupport.security;

import ma.atlasbank.contracts.payment.OrderSignatures;
import ma.atlasbank.contracts.payment.TransactionRequested;

/**
 * Fixed Ed25519 key pair for tests only (never deployed): the transaction service signs orders with
 * it and the ledger trusts it, so integration tests exchange orders exactly as in production.
 *
 * <p>Usage: {@code @TestPropertySource(properties = {TestOrderSigning.SIGNER_KEY_ID, TestOrderSigning.SIGNER_PRIVATE_KEY})}
 * on the signing side, {@code {TestOrderSigning.TRUSTED_KEY_ID, TestOrderSigning.TRUSTED_PUBLIC_KEY}} on the ledger side.
 */
public final class TestOrderSigning {

    public static final String KEY_ID = "test-key";
    public static final String PRIVATE_KEY = "MC4CAQAwBQYDK2VwBCIEIOb3b1cDYpCiKaAZ+m4xMtSmXoZuY2KVdZL/UiV+XKBm";
    public static final String PUBLIC_KEY = "MCowBQYDK2VwAyEA4CDmyZwb2Rj5qUP3JLTiaTcshoPHGlojfK/ZUGWF5Rc=";

    public static final String SIGNER_KEY_ID = "atlas.transactions.signing.key-id=" + KEY_ID;
    public static final String SIGNER_PRIVATE_KEY = "atlas.transactions.signing.private-key=" + PRIVATE_KEY;
    public static final String TRUSTED_KEY_ID = "atlas.accounts.order-signing.key-id=" + KEY_ID;
    public static final String TRUSTED_PUBLIC_KEY = "atlas.accounts.order-signing.public-key=" + PUBLIC_KEY;

    private TestOrderSigning() {
    }

    public static TransactionRequested sign(TransactionRequested order) {
        return OrderSignatures.sign(order, KEY_ID, OrderSignatures.privateKey(PRIVATE_KEY));
    }
}
