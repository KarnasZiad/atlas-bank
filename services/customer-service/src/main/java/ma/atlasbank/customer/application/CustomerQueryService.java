package ma.atlasbank.customer.application;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import ma.atlasbank.commons.security.Requester;
import ma.atlasbank.customer.application.port.CustomerHistory;
import ma.atlasbank.customer.application.port.CustomerRepository;
import ma.atlasbank.customer.application.view.CustomerRevisionView;
import ma.atlasbank.customer.application.view.CustomerSummaryView;
import ma.atlasbank.customer.application.view.CustomerView;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class CustomerQueryService {

    private final CustomerRepository customers;
    private final CustomerHistory history;
    private final CustomerFinder finder;
    private final CustomerViewMapper mapper;

    public CustomerView get(UUID customerId, Requester requester) {
        return mapper.toView(finder.getVisibleTo(customerId, requester));
    }

    /**
     * Profile of the signed-in customer, resolved from the identity token.
     */
    public CustomerView current(Requester requester) {
        return get(requester.requireCustomerId(), requester);
    }

    public Page<CustomerSummaryView> search(CustomerSearchCriteria criteria, Pageable pageable) {
        return customers.search(criteria, pageable).map(mapper::toSummary);
    }

    public Page<CustomerRevisionView> history(UUID customerId, Pageable pageable) {
        finder.getById(customerId);
        return history.revisionsOf(customerId, pageable);
    }
}
