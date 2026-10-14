package ma.atlasbank.account.infrastructure.signing;

import jakarta.validation.constraints.NotBlank;
import java.util.HashMap;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Public keys of the transaction service ({@code atlas.accounts.order-signing.*}). No default: the
 * ledger refuses to start without a trusted key.
 *
 * @param keyId        identifier of the key the transaction service currently signs with
 * @param publicKey    Base64 DER-encoded X.509 Ed25519 public key matching {@code keyId}
 * @param previousKeys keys still trusted during a rotation (orders signed before the switch may be
 *                     in flight), by key id
 */
@Validated
@ConfigurationProperties("atlas.accounts.order-signing")
public record OrderSigningProperties(
        @NotBlank String keyId,
        @NotBlank String publicKey,
        @DefaultValue Map<String, String> previousKeys) {

    Map<String, String> trustedKeys() {
        Map<String, String> trusted = new HashMap<>(previousKeys);
        trusted.put(keyId, publicKey);
        return trusted;
    }
}
