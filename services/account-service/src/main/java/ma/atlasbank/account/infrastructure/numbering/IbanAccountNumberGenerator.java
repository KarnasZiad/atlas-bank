package ma.atlasbank.account.infrastructure.numbering;

import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Collectors;
import ma.atlasbank.account.application.port.AccountNumberGenerator;
import org.iban4j.Iban;
import org.iban4j.bban.BbanEntryType;
import org.iban4j.bban.BbanStructure;
import org.iban4j.bban.BbanStructureEntry;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * Issues IBANs: a database sequence guarantees uniqueness across instances, iban4j provides the
 * national BBAN layout and the ISO 13616 check digits. The account number width is read from the
 * BBAN structure of the configured country instead of being hard-coded.
 */
@Component
class IbanAccountNumberGenerator implements AccountNumberGenerator {

    private static final Set<BbanEntryType> SUPPORTED_ENTRIES =
            EnumSet.of(BbanEntryType.bank_code, BbanEntryType.branch_code, BbanEntryType.account_number);

    private final JdbcClient jdbc;
    private final AccountNumberingProperties properties;
    private final String accountNumberFormat;

    IbanAccountNumberGenerator(JdbcClient jdbc, AccountNumberingProperties properties) {
        this.jdbc = jdbc;
        this.properties = properties;
        BbanStructure structure = BbanStructure.forCountry(properties.countryCode());
        Set<BbanEntryType> entries = structure.getEntries().stream()
                .map(BbanStructureEntry::getEntryType)
                .collect(Collectors.toSet());
        if (!SUPPORTED_ENTRIES.containsAll(entries)) {
            throw new IllegalStateException(
                    "BBAN layout of %s %s is not supported".formatted(properties.countryCode(), entries));
        }
        int width = structure.getEntries().stream()
                .filter(entry -> entry.getEntryType() == BbanEntryType.account_number)
                .mapToInt(BbanStructureEntry::getLength)
                .findFirst()
                .orElseThrow();
        this.accountNumberFormat = "%0" + width + "d";
    }

    @Override
    public String nextAccountNumber() {
        long sequence = jdbc.sql("select nextval('account_number_seq')").query(Long.class).single();
        return new Iban.Builder()
                .countryCode(properties.countryCode())
                .bankCode(properties.bankCode())
                .branchCode(properties.branchCode())
                .accountNumber(accountNumberFormat.formatted(sequence))
                .build()
                .toString();
    }
}
