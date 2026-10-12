package ma.atlasbank.account.domain;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Risk limits of an account, set from the product terms when the account is opened.
 *
 * @param overdraftLimit  how far below zero the balance may go
 * @param dailyDebitLimit total amount that may leave the account per business day
 */
public record AccountLimits(BigDecimal overdraftLimit, BigDecimal dailyDebitLimit) {

    public AccountLimits {
        Objects.requireNonNull(overdraftLimit, "overdraftLimit");
        Objects.requireNonNull(dailyDebitLimit, "dailyDebitLimit");
        if (overdraftLimit.signum() < 0 || dailyDebitLimit.signum() <= 0) {
            throw new IllegalArgumentException("Overdraft must be >= 0 and daily debit limit > 0");
        }
    }
}
