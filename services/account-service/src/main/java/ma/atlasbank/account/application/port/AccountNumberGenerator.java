package ma.atlasbank.account.application.port;

import java.util.Currency;

/**
 * Allocates account numbers.
 */
public interface AccountNumberGenerator {

    /** A new, unique and valid IBAN for a customer account. */
    String nextAccountNumber();

    /**
     * Number of the branch cash account in the given currency. It is deliberately not an IBAN, so it
     * can never be the source or target of an order placed through the API.
     */
    String tellerCashAccountNumber(Currency currency);
}
