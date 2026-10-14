package ma.atlasbank.customer.application;

import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import ma.atlasbank.commons.security.Requester;
import ma.atlasbank.contracts.customer.ContactDetailsChanged;
import ma.atlasbank.contracts.customer.CustomerSnapshot;
import ma.atlasbank.contracts.customer.CustomerStatus;
import ma.atlasbank.customer.domain.ContactField;
import ma.atlasbank.customer.domain.Customer;
import ma.atlasbank.platform.time.BusinessCalendar;
import org.springframework.stereotype.Component;

/**
 * Translates the aggregate into the published language. The national identity number and the
 * address are deliberately left out: other services have no need for them.
 *
 * <p>Once the relationship is closed, the other services have no reason to contact the customer any
 * more: the closing snapshot carries no personal data, and as the latest record of a compacted topic
 * it replaces (then erases) the earlier ones.
 */
@Component
@RequiredArgsConstructor
class CustomerSnapshotFactory {

    private final BusinessCalendar calendar;

    CustomerSnapshot snapshotOf(Customer customer) {
        CustomerStatus status = CustomerStatus.valueOf(customer.getStatus().name());
        if (status == CustomerStatus.CLOSED) {
            return new CustomerSnapshot(UUID.randomUUID(), calendar.now(), customer.getId(), customer.getVersion(),
                    null, null, null, null, null, status);
        }
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
                status);
    }

    ContactDetailsChanged contactChanged(Customer customer, Set<ContactField> changes, Requester requester) {
        return new ContactDetailsChanged(
                UUID.randomUUID(),
                calendar.now(),
                customer.getId(),
                changes.stream()
                        .map(change -> ContactDetailsChanged.ContactField.valueOf(change.name()))
                        .collect(Collectors.toUnmodifiableSet()),
                !requester.isStaff());
    }
}
