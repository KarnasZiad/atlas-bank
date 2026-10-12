package ma.atlasbank.transaction.infrastructure.messaging;

import lombok.RequiredArgsConstructor;
import ma.atlasbank.contracts.Topics;
import ma.atlasbank.contracts.ledger.TransactionOutcome;
import ma.atlasbank.transaction.application.OrderOutcomeRecorder;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class OutcomeListener {

    private final OrderOutcomeRecorder recorder;

    @KafkaListener(id = "order-outcomes", topics = Topics.TRANSACTION_OUTCOMES)
    void onOutcome(TransactionOutcome outcome) {
        recorder.record(outcome);
    }
}
