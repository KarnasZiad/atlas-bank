package ma.atlasbank.customer.infrastructure.persistence;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import ma.atlasbank.customer.application.port.CustomerHistory;
import ma.atlasbank.customer.application.view.CustomerRevisionView;
import ma.atlasbank.customer.domain.Customer;
import ma.atlasbank.platform.jpa.audit.AuditRevision;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.history.Revision;
import org.springframework.data.history.RevisionSort;
import org.springframework.stereotype.Component;

/**
 * Audit trail read from the Hibernate Envers tables, most recent change first.
 */
@Component
@RequiredArgsConstructor
class EnversCustomerHistory implements CustomerHistory {

    private final JpaCustomerRepository repository;

    @Override
    public Page<CustomerRevisionView> revisionsOf(UUID customerId, Pageable pageable) {
        Pageable newestFirst = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), RevisionSort.desc());
        return repository.findRevisions(customerId, newestFirst).map(EnversCustomerHistory::toView);
    }

    private static CustomerRevisionView toView(Revision<Long, Customer> revision) {
        Customer state = revision.getEntity();
        AuditRevision audit = revision.getMetadata().getDelegate();
        return new CustomerRevisionView(
                revision.getRequiredRevisionNumber(),
                revision.getRequiredRevisionInstant(),
                audit.getActor(),
                revision.getMetadata().getRevisionType().name(),
                state.getStatus(),
                state.getStatusReason(),
                state.getEmail(),
                state.getPhoneNumber());
    }
}
