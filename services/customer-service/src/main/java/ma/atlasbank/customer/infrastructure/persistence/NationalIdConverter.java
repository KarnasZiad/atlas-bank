package ma.atlasbank.customer.infrastructure.persistence;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import ma.atlasbank.customer.domain.NationalId;
import org.springframework.security.crypto.encrypt.TextEncryptor;

/**
 * Transparent encryption at rest of national identity numbers (AES-256-GCM, random IV).
 *
 * <p>Auto-applied to every {@link NationalId} attribute, so the domain model stays unaware of
 * cryptography. Hibernate obtains the converter from Spring, which injects the encryptor.
 */
@Converter(autoApply = true)
public class NationalIdConverter implements AttributeConverter<NationalId, String> {

    private final TextEncryptor encryptor;

    public NationalIdConverter(TextEncryptor personalDataEncryptor) {
        this.encryptor = personalDataEncryptor;
    }

    @Override
    public String convertToDatabaseColumn(NationalId nationalId) {
        return nationalId == null ? null : encryptor.encrypt(nationalId.value());
    }

    @Override
    public NationalId convertToEntityAttribute(String encrypted) {
        return encrypted == null ? null : new NationalId(encryptor.decrypt(encrypted));
    }
}
