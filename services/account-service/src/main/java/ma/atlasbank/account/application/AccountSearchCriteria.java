package ma.atlasbank.account.application;

import java.util.UUID;
import ma.atlasbank.account.domain.AccountStatus;
import ma.atlasbank.account.domain.AccountType;

/**
 * Optional filters of the account list.
 */
public record AccountSearchCriteria(UUID customerId, AccountStatus status, AccountType type) {

    /** Same filters, narrowed to the accounts of one customer. */
    public AccountSearchCriteria ownedBy(UUID owner) {
        return new AccountSearchCriteria(owner, status, type);
    }
}
