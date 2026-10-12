package ma.atlasbank.customer.application;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import ma.atlasbank.commons.error.ResourceNotFoundException;
import ma.atlasbank.commons.security.Requester;
import ma.atlasbank.customer.application.port.CustomerRepository;
import ma.atlasbank.customer.domain.Customer;
import ma.atlasbank.customer.domain.CustomerErrorCode;
import org.springframework.stereotype.Component;

/**
 * Loads customers and applies the ownership rule in one place: a customer only sees their own
 * file, staff see every file. Invisible and missing customers are reported identically.
 */
@Component
@RequiredArgsConstructor
class CustomerFinder {

    private final CustomerRepository customers;

    Customer getById(UUID id) {
        return customers.findById(id).orElseThrow(() -> notFound(id));
    }

    Customer getVisibleTo(UUID id, Requester requester) {
        if (!requester.canAccessCustomer(id)) {
            throw notFound(id);
        }
        return getById(id);
    }

    private static ResourceNotFoundException notFound(UUID id) {
        return new ResourceNotFoundException(CustomerErrorCode.CUSTOMER_NOT_FOUND, "Customer %s not found", id);
    }
}
