package ma.atlasbank.customer.infrastructure.persistence;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import ma.atlasbank.customer.application.CustomerSearchCriteria;
import ma.atlasbank.customer.domain.Customer;
import ma.atlasbank.customer.domain.Customer_;
import org.springframework.data.jpa.domain.Specification;

/**
 * Type-safe search predicates built on the generated JPA metamodel: renaming an attribute
 * breaks the build instead of a query at runtime.
 */
final class CustomerSpecifications {

    private CustomerSpecifications() {
    }

    static Specification<Customer> matching(CustomerSearchCriteria criteria) {
        List<Specification<Customer>> filters = new ArrayList<>();
        if (criteria.status() != null) {
            filters.add((root, query, cb) -> cb.equal(root.get(Customer_.status), criteria.status()));
        }
        if (criteria.hasQuery()) {
            String pattern = "%" + escapeLike(criteria.query().strip().toLowerCase(Locale.ROOT)) + "%";
            filters.add((root, query, cb) -> cb.or(
                    cb.like(cb.lower(root.get(Customer_.firstName)), pattern, '\\'),
                    cb.like(cb.lower(root.get(Customer_.lastName)), pattern, '\\'),
                    cb.like(root.get(Customer_.email), pattern, '\\')));
        }
        return Specification.allOf(filters);
    }

    /** User input is a literal fragment, not a pattern: neutralise LIKE wildcards. */
    private static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
