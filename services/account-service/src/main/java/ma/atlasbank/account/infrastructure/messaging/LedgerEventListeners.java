package ma.atlasbank.account.infrastructure.messaging;

import lombok.RequiredArgsConstructor;
import ma.atlasbank.account.application.AccountHolderProjector;
import ma.atlasbank.account.application.LedgerPostingService;
import ma.atlasbank.contracts.Topics;
import ma.atlasbank.contracts.customer.CustomerSnapshot;
import ma.atlasbank.contracts.payment.TransactionRequested;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Inbound adapter: translates Kafka deliveries into use-case calls. Retries, back-off and
 * dead-lettering are handled by the platform error handler.
 */
@Component
@RequiredArgsConstructor
class LedgerEventListeners {

    private final LedgerPostingService postingService;
    private final AccountHolderProjector holderProjector;

    @KafkaListener(id = "ledger-postings", topics = Topics.TRANSACTION_REQUESTED)
    void onTransactionRequested(TransactionRequested order) {
        postingService.post(order);
    }

    @KafkaListener(id = "account-holders", topics = Topics.CUSTOMER_SNAPSHOTS)
    void onCustomerSnapshot(CustomerSnapshot snapshot) {
        holderProjector.apply(snapshot);
    }
}
