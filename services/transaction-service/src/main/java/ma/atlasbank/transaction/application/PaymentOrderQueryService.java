package ma.atlasbank.transaction.application;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import ma.atlasbank.commons.error.BusinessRuleViolationException;
import ma.atlasbank.commons.error.ResourceNotFoundException;
import ma.atlasbank.commons.security.Requester;
import ma.atlasbank.platform.time.BusinessCalendar;
import ma.atlasbank.transaction.application.port.PaymentOrderRepository;
import ma.atlasbank.transaction.application.view.PaymentOrderView;
import ma.atlasbank.transaction.domain.PaymentOrder;
import ma.atlasbank.transaction.domain.TransactionErrorCode;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Order tracking. Customers see the orders they placed; staff see every order.
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class PaymentOrderQueryService {

    private final PaymentOrderRepository orders;
    private final PaymentOrderMapper mapper;
    private final BusinessCalendar calendar;

    public PaymentOrderView get(UUID orderId, Requester requester) {
        return orders.findById(orderId)
                .filter(order -> isVisibleTo(order, requester))
                .map(mapper::toView)
                .orElseThrow(() -> new ResourceNotFoundException(TransactionErrorCode.ORDER_NOT_FOUND,
                        "Order %s not found", orderId));
    }

    public Page<PaymentOrderView> search(OrderSearchCriteria criteria, Requester requester, Pageable pageable) {
        if (criteria.from() != null && criteria.to() != null && criteria.from().isAfter(criteria.to())) {
            throw new BusinessRuleViolationException(TransactionErrorCode.INVALID_PERIOD, "The period starts after it ends");
        }
        OrderSearchFilter filter = new OrderSearchFilter(
                criteria.status(),
                criteria.kind(),
                criteria.accountNumber(),
                criteria.from() == null ? null : calendar.startOf(criteria.from()),
                criteria.to() == null ? null : calendar.endOf(criteria.to()),
                requester.isStaff() ? null : requester.requireCustomerId());
        return orders.search(filter, pageable).map(mapper::toView);
    }

    private static boolean isVisibleTo(PaymentOrder order, Requester requester) {
        return requester.isStaff()
                || (order.getInitiator().getCustomerId() != null
                && order.getInitiator().getCustomerId().equals(requester.customerId()));
    }
}
