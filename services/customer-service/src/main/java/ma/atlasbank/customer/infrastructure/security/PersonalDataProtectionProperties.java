package ma.atlasbank.customer.infrastructure.security;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Key material protecting personal data. There are deliberately no defaults: the service refuses
 * to start until the deployment provides them (from a secret store, never from source control).
 *
 * @param encryptionPassword secret from which the AES-256 key is derived
 * @param encryptionSalt     hex-encoded salt of the key derivation
 * @param fingerprintKey     secret key of the HMAC used for duplicate detection
 */
@Validated
@ConfigurationProperties("atlas.customers.personal-data")
public record PersonalDataProtectionProperties(
        @NotBlank @Size(min = 16) String encryptionPassword,
        @NotBlank @Pattern(regexp = "^([0-9a-fA-F]{2}){8,}$", message = "must be a hex string of at least 8 bytes")
        String encryptionSalt,
        @NotBlank @Size(min = 32) String fingerprintKey) {
}
