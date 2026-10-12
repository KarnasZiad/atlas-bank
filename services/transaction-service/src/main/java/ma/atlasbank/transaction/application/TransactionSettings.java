package ma.atlasbank.transaction.application;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.util.Currency;
import java.util.Map;
import java.util.Optional;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Intake policy ({@code atlas.transactions.*}).
 *
 * @param maxAmountPerOrder ceiling of a single order per currency; a currency absent from the
 *                          map is not accepted at all
 */
@Validated
@ConfigurationProperties("atlas.transactions")
public record TransactionSettings(@NotEmpty Map<Currency, @Positive BigDecimal> maxAmountPerOrder) {

    public Optional<BigDecimal> ceilingFor(Currency currency) {
        return Optional.ofNullable(maxAmountPerOrder.get(currency));
    }
}
