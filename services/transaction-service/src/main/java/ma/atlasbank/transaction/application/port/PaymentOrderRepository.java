package ma.atlasbank.transaction.application.port;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;
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

    /**
     * Serializes the intake of one initiator until the end of the current transaction: a retry racing
     * the original request finds it, and limits cannot be bypassed by splitting orders across
     * parallel requests.
     */
    void lockIntakeOf(String initiatorSubject);

    /**
     * Total of the orders the initiator placed alone (without a second employee) since the given
     * instant, refused and rejected orders excluded.
     */
    BigDecimal sumPlacedWithoutApprovalSince(String initiatorSubject, Currency currency, Instant since);
}
