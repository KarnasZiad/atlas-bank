package ma.atlasbank.commons.money;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Currency;
import java.util.Objects;

/**
 * Immutable monetary amount.
 *
 * <p>The scale is derived from the ISO 4217 definition of the currency
 * ({@link Currency#getDefaultFractionDigits()}), so amounts carrying more decimals than the
 * currency allows are rejected instead of being silently rounded.
 */
public record Money(BigDecimal amount, Currency currency) implements Comparable<Money> {

    public Money {
        Objects.requireNonNull(amount, "amount must not be null");
        Objects.requireNonNull(currency, "currency must not be null");
        int fractionDigits = currency.getDefaultFractionDigits();
        if (fractionDigits < 0) {
            throw new InvalidMoneyException("Currency %s is not a monetary currency".formatted(currency));
        }
        try {
            amount = amount.setScale(fractionDigits, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException tooPrecise) {
            throw new InvalidMoneyException(
                    "Amount %s has more than the %d decimals allowed for %s".formatted(amount, fractionDigits, currency));
        }
    }

    public static Money of(BigDecimal amount, Currency currency) {
        return new Money(amount, currency);
    }

    public static Money of(BigDecimal amount, String currencyCode) {
        return new Money(amount, currencyOf(currencyCode));
    }

    /**
     * Resolves an ISO 4217 code, turning the JDK's generic exception into a domain-level one.
     */
    public static Currency currencyOf(String currencyCode) {
        try {
            return Currency.getInstance(Objects.requireNonNull(currencyCode, "currency code must not be null"));
        } catch (IllegalArgumentException unknown) {
            throw new InvalidMoneyException("Unknown currency code '%s'".formatted(currencyCode));
        }
    }

    public static Money zero(Currency currency) {
        return new Money(BigDecimal.ZERO, currency);
    }

    public Money add(Money other) {
        requireSameCurrency(other);
        return new Money(amount.add(other.amount), currency);
    }

    public Money subtract(Money other) {
        requireSameCurrency(other);
        return new Money(amount.subtract(other.amount), currency);
    }

    public Money negate() {
        return new Money(amount.negate(), currency);
    }

    public boolean isPositive() {
        return amount.signum() > 0;
    }

    public boolean isNegative() {
        return amount.signum() < 0;
    }

    public boolean isZero() {
        return amount.signum() == 0;
    }

    public boolean isGreaterThan(Money other) {
        return compareTo(other) > 0;
    }

    public boolean isLessThan(Money other) {
        return compareTo(other) < 0;
    }

    public boolean hasCurrency(Currency expected) {
        return currency.equals(expected);
    }

    @Override
    public int compareTo(Money other) {
        requireSameCurrency(other);
        return amount.compareTo(other.amount);
    }

    private void requireSameCurrency(Money other) {
        Objects.requireNonNull(other, "other must not be null");
        if (!currency.equals(other.currency)) {
            throw new CurrencyMismatchException(currency, other.currency);
        }
    }

    @Override
    public String toString() {
        return amount.toPlainString() + " " + currency.getCurrencyCode();
    }
}
