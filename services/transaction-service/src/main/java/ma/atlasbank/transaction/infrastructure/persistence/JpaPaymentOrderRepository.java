package ma.atlasbank.transaction.infrastructure.persistence;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;
import java.util.UUID;
import ma.atlasbank.transaction.application.OrderSearchFilter;
import ma.atlasbank.transaction.application.port.PaymentOrderRepository;
import ma.atlasbank.transaction.domain.OrderStatus;
import ma.atlasbank.transaction.domain.PaymentOrder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

interface JpaPaymentOrderRepository extends JpaRepository<PaymentOrder, UUID>, JpaSpecificationExecutor<PaymentOrder>,
        PaymentOrderRepository {

    @Override
    default Page<PaymentOrder> search(OrderSearchFilter filter, Pageable pageable) {
        return findAll(PaymentOrderSpecifications.matching(filter), pageable);
    }

    /** PostgreSQL transaction-level advisory lock, released at commit or rollback. */
    @Query(value = "select 1 from pg_advisory_xact_lock(hashtextextended(:initiatorSubject, 0))", nativeQuery = true)
    Integer acquireIntakeLock(String initiatorSubject);

    @Override
    default void lockIntakeOf(String initiatorSubject) {
        acquireIntakeLock(initiatorSubject);
    }

    @Query("""
            select coalesce(sum(o.amount), 0) from PaymentOrder o
            where o.initiator.subject = :initiatorSubject and o.currency = :currency and o.requestedAt >= :since
              and o.approvalRequired = false and o.status <> :excluded
            """)
    BigDecimal sumPlacedAlone(String initiatorSubject, Currency currency, Instant since, OrderStatus excluded);

    @Override
    default BigDecimal sumPlacedWithoutApprovalSince(String initiatorSubject, Currency currency, Instant since) {
        return sumPlacedAlone(initiatorSubject, currency, since, OrderStatus.REJECTED);
    }
}
