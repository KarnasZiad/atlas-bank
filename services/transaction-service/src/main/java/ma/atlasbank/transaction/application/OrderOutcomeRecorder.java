package ma.atlasbank.transaction.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ma.atlasbank.commons.error.ResourceNotFoundException;
import ma.atlasbank.contracts.ledger.TransactionOutcome;
import ma.atlasbank.contracts.ledger.TransactionOutcome.PostingResult;
import ma.atlasbank.transaction.application.port.PaymentOrderRepository;
import ma.atlasbank.transaction.domain.PaymentOrder;
import ma.atlasbank.transaction.domain.TransactionErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Settles orders with the outcome reported by the ledger. Redelivered outcomes are no-ops.
 */
@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class OrderOutcomeRecorder {

    private final PaymentOrderRepository orders;

    public void record(TransactionOutcome outcome) {
        PaymentOrder order = orders.findById(outcome.transactionId())
                .orElseThrow(() -> new ResourceNotFoundException(TransactionErrorCode.ORDER_NOT_FOUND,
                        "Outcome received for unknown order %s", outcome.transactionId()));
        if (outcome.result() == PostingResult.POSTED) {
            order.complete(outcome.occurredAt());
        } else {
            order.reject(outcome.rejectionCode(), outcome.occurredAt());
        }
        orders.save(order);
        log.info("Order {} settled as {}", order.getReference(), order.getStatus());
    }
}
