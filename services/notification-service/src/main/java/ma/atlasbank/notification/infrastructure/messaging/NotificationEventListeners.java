package ma.atlasbank.notification.infrastructure.messaging;

import lombok.RequiredArgsConstructor;
import ma.atlasbank.contracts.Topics;
import ma.atlasbank.contracts.account.AccountOpened;
import ma.atlasbank.contracts.account.AccountStatusChanged;
import ma.atlasbank.contracts.customer.ContactDetailsChanged;
import ma.atlasbank.contracts.customer.CustomerSnapshot;
import ma.atlasbank.contracts.ledger.TransactionOutcome;
import ma.atlasbank.notification.application.CustomerNotifier;
import ma.atlasbank.notification.application.RecipientDirectory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class NotificationEventListeners {

    private final RecipientDirectory recipients;
    private final CustomerNotifier notifier;

    @KafkaListener(id = "notification-recipients", topics = Topics.CUSTOMER_SNAPSHOTS)
    void onCustomerSnapshot(CustomerSnapshot snapshot) {
        recipients.apply(snapshot);
    }

    @KafkaListener(id = "notification-contact-changed", topics = Topics.CUSTOMER_CONTACT_CHANGED)
    void onContactDetailsChanged(ContactDetailsChanged event) {
        notifier.onContactDetailsChanged(event);
    }

    @KafkaListener(id = "notification-account-opened", topics = Topics.ACCOUNT_OPENED)
    void onAccountOpened(AccountOpened event) {
        notifier.onAccountOpened(event);
    }

    @KafkaListener(id = "notification-account-status", topics = Topics.ACCOUNT_STATUS_CHANGED)
    void onAccountStatusChanged(AccountStatusChanged event) {
        notifier.onAccountStatusChanged(event);
    }

    @KafkaListener(id = "notification-transaction-outcomes", topics = Topics.TRANSACTION_OUTCOMES)
    void onTransactionOutcome(TransactionOutcome outcome) {
        notifier.onTransactionOutcome(outcome);
    }
}
