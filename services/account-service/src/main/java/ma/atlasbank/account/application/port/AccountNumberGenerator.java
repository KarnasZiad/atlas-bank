package ma.atlasbank.account.application.port;

/**
 * Allocates a new, unique and valid account number (IBAN).
 */
public interface AccountNumberGenerator {

    String nextAccountNumber();
}
