package ma.atlasbank.transaction.application;

import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import ma.atlasbank.transaction.domain.OrderKind;
import ma.atlasbank.transaction.domain.OrderStatus;
import org.springframework.format.annotation.DateTimeFormat;

/**
 * Optional filters of the order history, as sent by API clients.
 *
 * @param accountNumber orders debiting or crediting this account
 * @param from          first business day included
 * @param to            last business day included
 */
public record OrderSearchCriteria(
        OrderStatus status,
        OrderKind kind,
        @Size(max = 34) String accountNumber,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
}
