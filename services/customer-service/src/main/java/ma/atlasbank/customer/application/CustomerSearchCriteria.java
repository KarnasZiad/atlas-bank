package ma.atlasbank.customer.application;

import jakarta.validation.constraints.Size;
import ma.atlasbank.customer.domain.CustomerStatus;

/**
 * Optional filters of the customer directory.
 *
 * @param query  case-insensitive fragment of the first name, last name or e-mail
 * @param status lifecycle status
 */
public record CustomerSearchCriteria(@Size(max = 100) String query, CustomerStatus status) {

    public boolean hasQuery() {
        return query != null && !query.isBlank();
    }
}
