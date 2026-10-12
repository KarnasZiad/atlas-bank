package ma.atlasbank.account.application;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;
import java.util.Currency;
import java.util.Map;
import java.util.Optional;
import ma.atlasbank.account.domain.AccountLimits;
import ma.atlasbank.account.domain.AccountType;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Product catalogue ({@code atlas.accounts.*}): which account type can be opened in which
 * currency, with which limits. A currency absent from a product is simply not offered.
 *
 * @param products           limits per product and currency
 * @param statementDaysByDefault period covered by a statement when no dates are given
 */
@Validated
@ConfigurationProperties("atlas.accounts")
public record AccountSettings(
        @NotEmpty Map<AccountType, Map<Currency, @Valid Limits>> products,
        @DefaultValue("30") @Positive int statementDaysByDefault) {

    public Optional<AccountLimits> limitsFor(AccountType type, Currency currency) {
        return Optional.ofNullable(products.getOrDefault(type, Map.of()).get(currency))
                .map(limits -> new AccountLimits(limits.overdraftLimit(), limits.dailyDebitLimit()));
    }

    public record Limits(@NotNull @PositiveOrZero BigDecimal overdraftLimit, @NotNull @Positive BigDecimal dailyDebitLimit) {
    }
}
