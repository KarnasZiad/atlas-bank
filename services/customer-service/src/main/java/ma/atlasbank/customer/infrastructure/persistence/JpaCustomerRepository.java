package ma.atlasbank.customer.infrastructure.persistence;

import java.util.UUID;
import ma.atlasbank.customer.application.CustomerSearchCriteria;
import ma.atlasbank.customer.application.port.CustomerRepository;
import ma.atlasbank.customer.domain.Customer;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.repository.history.RevisionRepository;

/**
 * Spring Data implements the application port directly: derived queries for the lookups,
 * specifications for the search, Envers for the history. No hand-written adapter is needed.
 */
interface JpaCustomerRepository extends
        JpaRepository<Customer, UUID>,
        JpaSpecificationExecutor<Customer>,
        RevisionRepository<Customer, UUID, Long>,
        CustomerRepository {

    @Override
    default Page<Customer> search(CustomerSearchCriteria criteria, Pageable pageable) {
        return findAll(CustomerSpecifications.matching(criteria), pageable);
    }
}
