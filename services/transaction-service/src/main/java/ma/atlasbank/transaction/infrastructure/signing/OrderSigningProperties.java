package ma.atlasbank.transaction.infrastructure.signing;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Key signing the orders handed to the ledger ({@code atlas.transactions.signing.*}). No default on
 * purpose: the service refuses to start until the deployment provides the key (from a secret store).
 *
 * @param keyId      identifier of the key, matched by the ledger against its trusted keys
 * @param privateKey Base64 of the DER-encoded PKCS#8 Ed25519 private key
 */
@Validated
@ConfigurationProperties("atlas.transactions.signing")
public record OrderSigningProperties(@NotBlank String keyId, @NotBlank String privateKey) {
}
