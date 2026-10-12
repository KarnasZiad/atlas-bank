package ma.atlasbank.transaction.infrastructure.reference;

import java.security.SecureRandom;
import java.time.format.DateTimeFormatter;
import ma.atlasbank.platform.time.BusinessCalendar;
import ma.atlasbank.transaction.application.port.ReferenceGenerator;
import ma.atlasbank.transaction.domain.OrderKind;
import org.springframework.stereotype.Component;

/**
 * References such as {@code TRF-20261012-7KQ2M9XA}: kind, business date, then random characters
 * from Crockford's base32 alphabet (no I, L, O, U: easy to read over the phone). Uniqueness is
 * guaranteed by a database constraint; 32^8 combinations per day make a collision negligible.
 */
@Component
class RandomReferenceGenerator implements ReferenceGenerator {

    private static final char[] ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ".toCharArray();
    private static final int RANDOM_LENGTH = 8;

    private final SecureRandom random = new SecureRandom();
    private final BusinessCalendar calendar;

    RandomReferenceGenerator(BusinessCalendar calendar) {
        this.calendar = calendar;
    }

    @Override
    public String nextReference(OrderKind kind) {
        StringBuilder reference = new StringBuilder(prefix(kind))
                .append('-')
                .append(calendar.today().format(DateTimeFormatter.BASIC_ISO_DATE))
                .append('-');
        for (int i = 0; i < RANDOM_LENGTH; i++) {
            reference.append(ALPHABET[random.nextInt(ALPHABET.length)]);
        }
        return reference.toString();
    }

    private static String prefix(OrderKind kind) {
        return switch (kind) {
            case TRANSFER -> "TRF";
            case DEPOSIT -> "DEP";
            case WITHDRAWAL -> "WDR";
        };
    }
}
