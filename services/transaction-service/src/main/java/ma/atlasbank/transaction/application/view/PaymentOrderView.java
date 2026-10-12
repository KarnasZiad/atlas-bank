package ma.atlasbank.transaction.application.view;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import ma.atlasbank.transaction.domain.InitiatorType;
import ma.atlasbank.transaction.domain.OrderKind;
import ma.atlasbank.transaction.domain.OrderStatus;

public record PaymentOrderView(
        UUID id,
        String reference,
        OrderKind kind,
        OrderStatus status,
        String sourceAccountNumber,
        String targetAccountNumber,
        BigDecimal amount,
        String currency,
        String description,
        String initiatedBy,
        InitiatorType initiatorType,
        String rejectionCode,
        Instant requestedAt,
        Instant completedAt) {
}
