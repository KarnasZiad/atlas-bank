package ma.atlasbank.contracts.payment;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.Map;
import java.util.Objects;

/**
 * Authenticity of payment orders, shared by the signer (transaction service) and the verifier
 * (ledger) so that both sides implement exactly the same scheme.
 *
 * <p>Ed25519 signatures (JDK provider, no third-party cryptography). Only the transaction service
 * holds the private key: anyone else able to write on the orders topic cannot produce an order
 * the ledger will post.
 */
public final class OrderSignatures {

    public static final String ALGORITHM = "Ed25519";

    private OrderSignatures() {
    }

    public static TransactionRequested sign(TransactionRequested order, String keyId, PrivateKey key) {
        Objects.requireNonNull(keyId, "keyId");
        try {
            Signature signer = Signature.getInstance(ALGORITHM);
            signer.initSign(key);
            signer.update(order.signedContent().getBytes(StandardCharsets.UTF_8));
            return order.withSignature(new OrderSignature(keyId, Base64.getEncoder().encodeToString(signer.sign())));
        } catch (GeneralSecurityException failure) {
            throw new IllegalStateException("Unable to sign payment order " + order.reference(), failure);
        }
    }

    /**
     * @param trustedKeys public keys accepted by the verifier, by key id
     * @return whether the order carries a valid signature from one of the trusted keys
     */
    public static boolean isAuthentic(TransactionRequested order, Map<String, PublicKey> trustedKeys) {
        OrderSignature signature = order.signature();
        if (signature == null || signature.keyId() == null || signature.value() == null) {
            return false;
        }
        PublicKey key = trustedKeys.get(signature.keyId());
        if (key == null) {
            return false;
        }
        try {
            Signature verifier = Signature.getInstance(ALGORITHM);
            verifier.initVerify(key);
            verifier.update(order.signedContent().getBytes(StandardCharsets.UTF_8));
            return verifier.verify(Base64.getDecoder().decode(signature.value()));
        } catch (GeneralSecurityException | IllegalArgumentException invalid) {
            return false;
        }
    }

    /**
     * @param base64Pkcs8 Base64 of the DER-encoded PKCS#8 private key
     *                    ({@code openssl genpkey -algorithm ed25519 -outform DER | base64})
     */
    public static PrivateKey privateKey(String base64Pkcs8) {
        try {
            return KeyFactory.getInstance(ALGORITHM)
                    .generatePrivate(new PKCS8EncodedKeySpec(Base64.getDecoder().decode(base64Pkcs8.strip())));
        } catch (GeneralSecurityException | IllegalArgumentException invalid) {
            throw new IllegalArgumentException("Not a Base64 PKCS#8 " + ALGORITHM + " private key", invalid);
        }
    }

    /**
     * @param base64X509 Base64 of the DER-encoded X.509 public key
     *                   ({@code openssl pkey -pubout -outform DER | base64})
     */
    public static PublicKey publicKey(String base64X509) {
        try {
            return KeyFactory.getInstance(ALGORITHM)
                    .generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(base64X509.strip())));
        } catch (GeneralSecurityException | IllegalArgumentException invalid) {
            throw new IllegalArgumentException("Not a Base64 X.509 " + ALGORITHM + " public key", invalid);
        }
    }
}
