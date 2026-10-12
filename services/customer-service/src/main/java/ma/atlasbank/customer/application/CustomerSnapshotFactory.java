package ma.atlasbank.customer.application;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import ma.atlasbank.contracts.customer.CustomerSnapshot;
import ma.atlasbank.contracts.customer.CustomerStatus;
import ma.atlasbank.customer.domain.Customer;
import ma.atlasbank.platform.time.BusinessCalendar;
import org.springframework.stereotype.Component;

/**
 * Translates the aggregate into the published language. The national identity number and the
 * address are deliberately left out: other services have no need for them.
 */
@Component
@RequiredArgsConstructor
class CustomerSnapshotFactory {

    private final BusinessCalendar calendar;

    CustomerSnapshot snapshotOf(Customer customer) {
        return new CustomerSnapshot(
                UUID.randomUUID(),
                calendar.now(),
                customer.getId(),
                customer.getVersion(),
                customer.getFirstName(),
                customer.getLastName(),
                customer.getEmail(),
                customer.getPhoneNumber(),
                customer.getPreferredLanguage().toLanguageTag(),
                CustomerStatus.valueOf(customer.getStatus().name()));
    }
}
