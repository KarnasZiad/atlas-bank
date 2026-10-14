package ma.atlasbank.notification.application;

import static ma.atlasbank.notification.domain.AccountNumbers.mask;

import java.util.List;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import ma.atlasbank.commons.money.Money;
import ma.atlasbank.contracts.account.AccountOpened;
import ma.atlasbank.contracts.account.AccountStatusChanged;
import ma.atlasbank.contracts.ledger.TransactionOutcome;
import ma.atlasbank.contracts.ledger.TransactionOutcome.Party;
import ma.atlasbank.contracts.ledger.TransactionOutcome.PostingResult;
import ma.atlasbank.notification.application.port.MessageSender;
import ma.atlasbank.notification.application.port.NotificationRenderer;
import ma.atlasbank.notification.application.port.NotificationRepository;
import ma.atlasbank.notification.application.port.RecipientRepository;
import ma.atlasbank.notification.domain.Channel;
import ma.atlasbank.notification.domain.LocalizedLabel;
import ma.atlasbank.notification.domain.Notification;
import ma.atlasbank.notification.domain.NotificationType;
import ma.atlasbank.notification.domain.Recipient;
import ma.atlasbank.notification.domain.RenderedMessage;
import ma.atlasbank.platform.time.BusinessCalendar;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Turns business events into customer notifications.
 *
 * <p>Delivery is at-least-once: the notification is recorded after the message is handed to the
 * channel, so a crash in between may resend it, but an event redelivered after a successful
 * notification is recognised and ignored. Each notification commits on its own, so that retrying
 * an event (e.g. one recipient not known yet) never resends the notifications already delivered.
 */
@Slf4j
@Service
public class CustomerNotifier {

    private final RecipientRepository recipients;
    private final NotificationRepository notifications;
    private final NotificationRenderer renderer;
    private final MessageSender sender;
    private final BusinessCalendar calendar;
    private final TransactionTemplate transaction;

    CustomerNotifier(RecipientRepository recipients, NotificationRepository notifications, NotificationRenderer renderer,
                     MessageSender sender, BusinessCalendar calendar, PlatformTransactionManager transactionManager) {
        this.recipients = recipients;
        this.notifications = notifications;
        this.renderer = renderer;
        this.sender = sender;
        this.calendar = calendar;
        this.transaction = new TransactionTemplate(transactionManager);
    }

    public void onAccountOpened(AccountOpened event) {
        notify(event.customerId(), NotificationType.ACCOUNT_OPENED, event.eventId(), recipient -> List.of(
                recipient.getFullName(), mask(event.accountNumber()), LocalizedLabel.of(event.accountType()), event.currency()));
    }

    /**
     * The reason typed by the employee is an internal note (it may mention an ongoing investigation):
     * the customer is told the new status only, never why it was decided.
     */
    public void onAccountStatusChanged(AccountStatusChanged event) {
        notify(event.customerId(), NotificationType.ACCOUNT_STATUS_CHANGED, event.eventId(), recipient -> List.of(
                recipient.getFullName(), mask(event.accountNumber()), LocalizedLabel.of(event.status())));
    }

    public void onTransactionOutcome(TransactionOutcome outcome) {
        Money amount = Money.of(outcome.amount(), outcome.currency());
        if (outcome.result() == PostingResult.REJECTED) {
            notify(outcome.initiatorCustomerId(), NotificationType.TRANSACTION_REJECTED, outcome.eventId(),
                    recipient -> List.of(recipient.getFullName(), amount, outcome.reference(),
                            LocalizedLabel.rejection(outcome.rejectionCode())));
            return;
        }
        switch (outcome.kind()) {
            case TRANSFER -> {
                notify(customerOf(outcome.debited()), NotificationType.TRANSFER_SENT, outcome.eventId(),
                        recipient -> List.of(recipient.getFullName(), amount, mask(outcome.debited().accountNumber()),
                                mask(outcome.credited().accountNumber()), outcome.reference()));
                notify(customerOf(outcome.credited()), NotificationType.TRANSFER_RECEIVED, outcome.eventId(),
                        recipient -> List.of(recipient.getFullName(), amount, mask(outcome.credited().accountNumber()),
                                mask(outcome.debited().accountNumber()), outcome.reference()));
            }
            case DEPOSIT -> notify(customerOf(outcome.credited()), NotificationType.DEPOSIT_RECEIVED, outcome.eventId(),
                    recipient -> List.of(recipient.getFullName(), amount, mask(outcome.credited().accountNumber()),
                            outcome.reference()));
            case WITHDRAWAL -> notify(customerOf(outcome.debited()), NotificationType.WITHDRAWAL_MADE, outcome.eventId(),
                    recipient -> List.of(recipient.getFullName(), amount, mask(outcome.debited().accountNumber()),
                            outcome.reference()));
        }
    }

    private void notify(UUID customerId, NotificationType type, UUID sourceEventId, Arguments arguments) {
        if (customerId != null) {
            transaction.executeWithoutResult(status -> deliver(customerId, type, sourceEventId, arguments));
        }
    }

    private void deliver(UUID customerId, NotificationType type, UUID sourceEventId, Arguments arguments) {
        if (notifications.existsBySourceEventIdAndCustomerIdAndType(sourceEventId, customerId, type)) {
            log.debug("{} for event {} already sent to customer {}", type, sourceEventId, customerId);
            return;
        }
        Recipient recipient = recipients.findById(customerId)
                .orElseThrow(() -> new RecipientNotYetKnownException(customerId));
        RenderedMessage message = renderer.render(type, recipient.getLanguage(), arguments.of(recipient));
        sender.send(recipient.getEmail(), message);
        notifications.save(Notification.sent(customerId, type, Channel.EMAIL, sourceEventId, recipient.getEmail(),
                message, calendar.now()));
        log.info("{} sent to customer {}", type, customerId);
    }

    private static UUID customerOf(Party party) {
        return party == null ? null : party.customerId();
    }

    @FunctionalInterface
    private interface Arguments {
        List<Object> of(Recipient recipient);
    }
}
