package ma.atlasbank.commons.money;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.Currency;
import org.junit.jupiter.api.Test;

class MoneyTest {

    private static final Currency MAD = Currency.getInstance("MAD");
    private static final Currency EUR = Currency.getInstance("EUR");
    private static final Currency JPY = Currency.getInstance("JPY");

    @Test
    void normalisesScaleToCurrencyFractionDigits() {
        assertThat(Money.of(new BigDecimal("10"), MAD).amount()).isEqualByComparingTo("10.00");
        assertThat(Money.of(new BigDecimal("10"), MAD).amount().scale()).isEqualTo(2);
        assertThat(Money.of(new BigDecimal("10"), JPY).amount().scale()).isZero();
    }

    @Test
    void rejectsAmountsMorePreciseThanTheCurrencyAllows() {
        assertThatThrownBy(() -> Money.of(new BigDecimal("10.001"), MAD))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("decimals");
    }

    @Test
    void addsAndSubtractsWithinTheSameCurrency() {
        Money balance = Money.of(new BigDecimal("100.50"), MAD);

        assertThat(balance.add(Money.of(new BigDecimal("0.50"), MAD))).isEqualTo(Money.of(new BigDecimal("101"), MAD));
        assertThat(balance.subtract(Money.of(new BigDecimal("200"), MAD)).isNegative()).isTrue();
    }

    @Test
    void refusesToMixCurrencies() {
        Money mad = Money.of(BigDecimal.ONE, MAD);
        Money eur = Money.of(BigDecimal.ONE, EUR);

        assertThatThrownBy(() -> mad.add(eur)).isInstanceOf(CurrencyMismatchException.class);
        assertThatThrownBy(() -> mad.compareTo(eur)).isInstanceOf(CurrencyMismatchException.class);
    }

    @Test
    void comparesAmounts() {
        Money small = Money.of(BigDecimal.ONE, MAD);
        Money large = Money.of(BigDecimal.TEN, MAD);

        assertThat(large.isGreaterThan(small)).isTrue();
        assertThat(small.isLessThan(large)).isTrue();
        assertThat(Money.zero(MAD).isZero()).isTrue();
    }
}
