package ma.atlasbank.customer.infrastructure.security;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import ma.atlasbank.customer.application.port.NationalIdFingerprinter;
import ma.atlasbank.customer.domain.NationalId;
import org.springframework.stereotype.Component;

/**
 * Blind index of national identity numbers: HMAC-SHA256 with a secret key, so the fingerprint
 * cannot be reversed by brute-forcing the small space of identity numbers without the key.
 */
@Component
class HmacNationalIdFingerprinter implements NationalIdFingerprinter {

    private static final String ALGORITHM = "HmacSHA256";

    private final SecretKeySpec key;

    HmacNationalIdFingerprinter(PersonalDataProtectionProperties properties) {
        this.key = new SecretKeySpec(properties.fingerprintKey().getBytes(StandardCharsets.UTF_8), ALGORITHM);
    }

    @Override
    public String fingerprint(NationalId nationalId) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(key);
            return HexFormat.of().formatHex(mac.doFinal(nationalId.value().getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException unavailable) {
            throw new IllegalStateException("HMAC-SHA256 is not available", unavailable);
        }
    }
}
