package ma.atlasbank.transaction.infrastructure.persistence;

import java.util.ArrayList;
import java.util.List;
import ma.atlasbank.transaction.application.OrderSearchFilter;
import ma.atlasbank.transaction.domain.Initiator_;
import ma.atlasbank.transaction.domain.PaymentOrder;
import ma.atlasbank.transaction.domain.PaymentOrder_;
import org.springframework.data.jpa.domain.Specification;

final class PaymentOrderSpecifications {

    private PaymentOrderSpecifications() {
    }

    static Specification<PaymentOrder> matching(OrderSearchFilter filter) {
        List<Specification<PaymentOrder>> filters = new ArrayList<>();
        if (filter.initiatorCustomerId() != null) {
            filters.add((root, query, cb) -> cb.equal(
                    root.get(PaymentOrder_.initiator).get(Initiator_.customerId), filter.initiatorCustomerId()));
        }
        if (filter.status() != null) {
            filters.add((root, query, cb) -> cb.equal(root.get(PaymentOrder_.status), filter.status()));
        }
        if (filter.kind() != null) {
            filters.add((root, query, cb) -> cb.equal(root.get(PaymentOrder_.kind), filter.kind()));
        }
        if (filter.accountNumber() != null && !filter.accountNumber().isBlank()) {
            filters.add((root, query, cb) -> cb.or(
                    cb.equal(root.get(PaymentOrder_.sourceAccountNumber), filter.accountNumber()),
                    cb.equal(root.get(PaymentOrder_.targetAccountNumber), filter.accountNumber())));
        }
        if (filter.from() != null) {
            filters.add((root, query, cb) -> cb.greaterThanOrEqualTo(root.get(PaymentOrder_.requestedAt), filter.from()));
        }
        if (filter.to() != null) {
            filters.add((root, query, cb) -> cb.lessThan(root.get(PaymentOrder_.requestedAt), filter.to()));
        }
        return Specification.allOf(filters);
    }
}
