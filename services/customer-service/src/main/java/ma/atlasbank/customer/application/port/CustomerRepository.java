package ma.atlasbank.customer.application.port;

import java.util.Optional;
import java.util.UUID;
import ma.atlasbank.customer.application.CustomerSearchCriteria;
import ma.atlasbank.customer.domain.Customer;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Persistence port of the customer aggregate.
 */
public interface CustomerRepository {

    /**
     * Saves and flushes, so that the returned aggregate carries its new version: published
     * snapshots are ordered by that version.
     */
    Customer saveAndFlush(Customer customer);

    Optional<Customer> findById(UUID id);

    boolean existsByEmail(String email);

    boolean existsByNationalIdFingerprint(String fingerprint);

    Page<Customer> search(CustomerSearchCriteria criteria, Pageable pageable);
}
