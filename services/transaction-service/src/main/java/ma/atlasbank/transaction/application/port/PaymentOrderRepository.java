package ma.atlasbank.transaction.application.port;

import java.util.Optional;
import java.util.UUID;
import ma.atlasbank.transaction.application.OrderSearchFilter;
import ma.atlasbank.transaction.domain.PaymentOrder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface PaymentOrderRepository {

    PaymentOrder save(PaymentOrder order);

    /** Flushes immediately so that a concurrent duplicate is detected inside the request. */
    PaymentOrder saveAndFlush(PaymentOrder order);

    Optional<PaymentOrder> findById(UUID id);

    Optional<PaymentOrder> findByInitiatorSubjectAndIdempotencyKey(String initiatorSubject, String idempotencyKey);

    Page<PaymentOrder> search(OrderSearchFilter filter, Pageable pageable);
}
