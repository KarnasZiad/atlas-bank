package ma.atlasbank.transaction.application;

import java.time.Duration;
import java.time.Instant;
import lombok.extern.slf4j.Slf4j;
import ma.atlasbank.platform.time.BusinessCalendar;
import ma.atlasbank.transaction.application.port.PaymentOrderRepository;
import ma.atlasbank.transaction.domain.OrderStatus;
import ma.atlasbank.transaction.domain.PaymentOrder;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Housekeeping of the order book, run periodically:
 * <ul>
 *   <li>staff orders nobody reviewed during their business day are rejected ({@code APPROVAL_EXPIRED});</li>
 *   <li>orders handed to the ledger but still without an outcome after a while are reported: the ledger
 *       parked them on a dead-letter topic, or it is down. Their money has not moved.</li>
 * </ul>
 */
@Slf4j
@Service
public class OrderMaintenance {

    private final PaymentOrderRepository orders;
    private final BusinessCalendar calendar;
    private final Duration stuckAfter;
    private final TransactionTemplate transaction;

    OrderMaintenance(PaymentOrderRepository orders, BusinessCalendar calendar, TransactionSettings settings,
                     PlatformTransactionManager transactionManager) {
        this.orders = orders;
        this.calendar = calendar;
        this.stuckAfter = settings.pendingOrderAlertAfter();
        this.transaction = new TransactionTemplate(transactionManager);
    }

    /**
     * Each order expires in its own transaction: an approval racing the expiry wins or loses on the
     * version of that order only.
     *
     * @return number of orders expired
     */
    public int expireUnreviewedOrders() {
        Instant startOfToday = calendar.startOfToday();
        int expired = 0;
        for (PaymentOrder candidate : orders.findAllByStatusAndRequestedAtBefore(OrderStatus.AWAITING_APPROVAL, startOfToday)) {
            try {
                boolean expiredNow = Boolean.TRUE.equals(transaction.execute(status -> orders.findById(candidate.getId())
                        .filter(order -> order.getStatus() == OrderStatus.AWAITING_APPROVAL)
                        .map(order -> {
                            order.expire(calendar.now());
                            orders.saveAndFlush(order);
                            return true;
                        })
                        .orElse(false)));
                if (expiredNow) {
                    expired++;
                    log.info("Order {} expired: not reviewed during its business day", candidate.getReference());
                }
            } catch (OptimisticLockingFailureException reviewedMeanwhile) {
                log.info("Order {} was reviewed while expiring, left as reviewed", candidate.getReference());
            }
        }
        return expired;
    }

    /**
     * @return number of orders handed to the ledger longer ago than the alert threshold and still pending
     */
    public long countStuckOrders() {
        long stuck = orders.countByStatusAndRequestedAtBefore(OrderStatus.PENDING, calendar.now().minus(stuckAfter));
        if (stuck > 0) {
            log.warn("{} order(s) pending for more than {}: check the ledger and its dead-letter topic", stuck, stuckAfter);
        }
        return stuck;
    }
}
