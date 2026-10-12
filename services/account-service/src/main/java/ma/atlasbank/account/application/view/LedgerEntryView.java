package ma.atlasbank.account.application.view;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import ma.atlasbank.account.domain.EntryDirection;
import ma.atlasbank.account.domain.TransactionKind;

public record LedgerEntryView(
        UUID id,
        UUID transactionId,
        String transactionReference,
        TransactionKind kind,
        EntryDirection direction,
        BigDecimal amount,
        String currency,
        BigDecimal balanceAfter,
        String counterpartyAccountNumber,
        String description,
        Instant bookedAt) {
}
