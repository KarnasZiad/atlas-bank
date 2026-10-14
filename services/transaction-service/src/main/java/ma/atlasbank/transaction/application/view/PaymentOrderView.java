package ma.atlasbank.transaction.application.view;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import ma.atlasbank.transaction.domain.InitiatorType;
import ma.atlasbank.transaction.domain.OrderKind;
import ma.atlasbank.transaction.domain.OrderStatus;

/**
 * @param approvalRequired whether the order exceeded what one employee may do alone
 * @param reviewedBy       employee who approved or refused the order, if it needed a review
 */
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
        boolean approvalRequired,
        String reviewedBy,
        Instant reviewedAt,
        Instant requestedAt,
        Instant completedAt) {
}
