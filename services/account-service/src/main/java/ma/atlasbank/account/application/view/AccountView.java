package ma.atlasbank.account.application.view;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import ma.atlasbank.account.domain.AccountStatus;
import ma.atlasbank.account.domain.AccountType;

/**
 * Amounts are expressed with the number of decimals of the account currency.
 */
public record AccountView(
        UUID id,
        String accountNumber,
        UUID customerId,
        AccountType type,
        String currency,
        BigDecimal balance,
        BigDecimal availableBalance,
        BigDecimal overdraftLimit,
        BigDecimal dailyDebitLimit,
        AccountStatus status,
        String statusReason,
        Instant openedAt,
        Instant closedAt) {
}
