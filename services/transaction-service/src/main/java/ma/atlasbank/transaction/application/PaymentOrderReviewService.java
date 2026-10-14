package ma.atlasbank.transaction.application;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ma.atlasbank.commons.error.ResourceNotFoundException;
import ma.atlasbank.commons.security.Requester;
import ma.atlasbank.platform.time.BusinessCalendar;
import ma.atlasbank.transaction.application.port.PaymentOrderRepository;
import ma.atlasbank.transaction.application.view.PaymentOrderView;
import ma.atlasbank.transaction.domain.PaymentOrder;
import ma.atlasbank.transaction.domain.Reviewer;
import ma.atlasbank.transaction.domain.TransactionErrorCode;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Second pair of eyes on staff orders. An approved order is handed to the ledger through the outbox,
 * atomically with the approval; two employees reviewing the same order concurrently are told apart
 * by optimistic locking, so it is never published twice.
 */
@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class PaymentOrderReviewService {

    private final PaymentOrderRepository orders;
    private final PaymentOrderMapper mapper;
    private final TransactionRequestedFactory events;
    private final ApplicationEventPublisher publisher;
    private final BusinessCalendar calendar;

    public PaymentOrderView approve(UUID orderId, Requester requester) {
        PaymentOrder order = find(orderId);
        order.approve(reviewerOf(requester), calendar.now());
        PaymentOrder approved = orders.saveAndFlush(order);
        publisher.publishEvent(events.requested(approved));
        log.info("Order {} approved by {}", approved.getReference(), requester.username());
        return mapper.toView(approved);
    }

    public PaymentOrderView refuse(UUID orderId, Requester requester) {
        PaymentOrder order = find(orderId);
        order.refuse(reviewerOf(requester), calendar.now());
        log.info("Order {} refused by {}", order.getReference(), requester.username());
        return mapper.toView(orders.saveAndFlush(order));
    }

    private PaymentOrder find(UUID orderId) {
        return orders.findById(orderId).orElseThrow(() -> new ResourceNotFoundException(
                TransactionErrorCode.ORDER_NOT_FOUND, "Order %s not found", orderId));
    }

    private static Reviewer reviewerOf(Requester requester) {
        return new Reviewer(requester.subject(), requester.username());
    }
}
