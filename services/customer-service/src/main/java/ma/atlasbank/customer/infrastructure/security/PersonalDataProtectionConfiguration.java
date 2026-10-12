package ma.atlasbank.customer.infrastructure.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.encrypt.Encryptors;
import org.springframework.security.crypto.encrypt.TextEncryptor;

@Configuration(proxyBeanMethods = false)
class PersonalDataProtectionConfiguration {

    /**
     * AES-256 in GCM mode (authenticated encryption) with a random IV per value, as provided by
     * Spring Security Crypto: no home-made cryptography.
     */
    @Bean
    TextEncryptor personalDataEncryptor(PersonalDataProtectionProperties properties) {
        return Encryptors.delux(properties.encryptionPassword(), properties.encryptionSalt());
    }
}
