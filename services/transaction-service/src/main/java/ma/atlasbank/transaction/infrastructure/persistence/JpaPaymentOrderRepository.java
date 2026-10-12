package ma.atlasbank.transaction.infrastructure.persistence;

import java.util.UUID;
import ma.atlasbank.transaction.application.OrderSearchFilter;
import ma.atlasbank.transaction.application.port.PaymentOrderRepository;
import ma.atlasbank.transaction.domain.PaymentOrder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

interface JpaPaymentOrderRepository extends JpaRepository<PaymentOrder, UUID>, JpaSpecificationExecutor<PaymentOrder>,
        PaymentOrderRepository {

    @Override
    default Page<PaymentOrder> search(OrderSearchFilter filter, Pageable pageable) {
        return findAll(PaymentOrderSpecifications.matching(filter), pageable);
    }
}
