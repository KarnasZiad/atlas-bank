package ma.atlasbank.commons.money;

import java.util.Currency;

/**
 * Raised when an arithmetic or comparison operation mixes two currencies.
 * Currency conversion is an explicit business operation, never an implicit side effect.
 */
public class CurrencyMismatchException extends IllegalArgumentException {

    public CurrencyMismatchException(Currency expected, Currency actual) {
        super("Currency mismatch: expected %s but was %s".formatted(expected, actual));
    }
}
