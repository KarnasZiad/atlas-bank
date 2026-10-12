package ma.atlasbank.transaction.application;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import ma.atlasbank.contracts.payment.TransactionKind;
import ma.atlasbank.contracts.payment.TransactionRequested;
import ma.atlasbank.contracts.payment.TransactionRequested.Initiator;
import ma.atlasbank.contracts.payment.TransactionRequested.InitiatorType;
import ma.atlasbank.platform.time.BusinessCalendar;
import ma.atlasbank.transaction.domain.PaymentOrder;
import org.springframework.stereotype.Component;

/**
 * Translates an accepted order into the published language understood by the ledger.
 */
@Component
@RequiredArgsConstructor
class TransactionRequestedFactory {

    private final BusinessCalendar calendar;

    TransactionRequested requested(PaymentOrder order) {
        return new TransactionRequested(
                UUID.randomUUID(),
                calendar.now(),
                order.getId(),
                order.getReference(),
                TransactionKind.valueOf(order.getKind().name()),
                order.getSourceAccountNumber(),
                order.getTargetAccountNumber(),
                order.money().amount(),
                order.getCurrency().getCurrencyCode(),
                order.getDescription(),
                new Initiator(
                        order.getInitiator().getSubject(),
                        order.getInitiator().getUsername(),
                        order.getInitiator().getCustomerId(),
                        InitiatorType.valueOf(order.getInitiator().getType().name())));
    }
}
