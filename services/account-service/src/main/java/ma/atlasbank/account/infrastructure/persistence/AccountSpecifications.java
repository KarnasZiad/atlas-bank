package ma.atlasbank.account.infrastructure.persistence;

import java.util.ArrayList;
import java.util.List;
import ma.atlasbank.account.application.AccountSearchCriteria;
import ma.atlasbank.account.domain.Account;
import ma.atlasbank.account.domain.Account_;
import org.springframework.data.jpa.domain.Specification;

final class AccountSpecifications {

    private AccountSpecifications() {
    }

    static Specification<Account> matching(AccountSearchCriteria criteria) {
        List<Specification<Account>> filters = new ArrayList<>();
        if (criteria.customerId() != null) {
            filters.add((root, query, cb) -> cb.equal(root.get(Account_.customerId), criteria.customerId()));
        }
        if (criteria.status() != null) {
            filters.add((root, query, cb) -> cb.equal(root.get(Account_.status), criteria.status()));
        }
        if (criteria.type() != null) {
            filters.add((root, query, cb) -> cb.equal(root.get(Account_.type), criteria.type()));
        }
        return Specification.allOf(filters);
    }
}
