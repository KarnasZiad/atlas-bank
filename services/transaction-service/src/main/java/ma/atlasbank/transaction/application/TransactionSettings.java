package ma.atlasbank.transaction.application;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;
import java.util.Currency;
import java.util.Map;
import java.util.Optional;
import ma.atlasbank.commons.money.Money;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Intake policy ({@code atlas.transactions.*}).
 *
 * @param maxAmountPerOrder ceiling of a single order per currency; a currency absent from the
 *                          map is not accepted at all
 * @param staffControls     four-eyes limits of the orders placed by bank staff, per currency; in a
 *                          currency absent from the map, every staff order needs a second employee
 */
@Validated
@ConfigurationProperties("atlas.transactions")
public record TransactionSettings(
        @NotEmpty Map<Currency, @Positive BigDecimal> maxAmountPerOrder,
        @NotNull Map<Currency, @Valid StaffControl> staffControls) {

    public Optional<BigDecimal> ceilingFor(Currency currency) {
        return Optional.ofNullable(maxAmountPerOrder.get(currency));
    }

    /**
     * @param placedAloneToday total of the orders the employee already placed today without approval
     */
    public boolean staffOrderRequiresApproval(Money amount, BigDecimal placedAloneToday) {
        return Optional.ofNullable(staffControls.get(amount.currency()))
                .map(control -> control.requiresApproval(amount.amount(), placedAloneToday))
                .orElse(true);
    }

    /**
     * What one employee may do without a second pair of eyes.
     *
     * @param approvalThreshold         largest single order an employee may place alone
     * @param dailyLimitWithoutApproval total an employee may place alone per business day; beyond it,
     *                                  every order needs approval, whatever its amount
     */
    public record StaffControl(
            @NotNull @PositiveOrZero BigDecimal approvalThreshold,
            @NotNull @PositiveOrZero BigDecimal dailyLimitWithoutApproval) {

        boolean requiresApproval(BigDecimal amount, BigDecimal placedAloneToday) {
            return amount.compareTo(approvalThreshold) > 0
                    || placedAloneToday.add(amount).compareTo(dailyLimitWithoutApproval) > 0;
        }
    }
}
