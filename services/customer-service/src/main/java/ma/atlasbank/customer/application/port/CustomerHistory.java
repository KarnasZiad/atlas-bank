package ma.atlasbank.customer.application.port;

import java.util.UUID;
import ma.atlasbank.customer.application.view.CustomerRevisionView;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Read access to the audit trail of a customer (every committed change, with its author).
 */
public interface CustomerHistory {

    Page<CustomerRevisionView> revisionsOf(UUID customerId, Pageable pageable);
}
