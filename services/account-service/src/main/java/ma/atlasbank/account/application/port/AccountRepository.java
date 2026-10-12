package ma.atlasbank.account.application.port;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import ma.atlasbank.account.application.AccountSearchCriteria;
import ma.atlasbank.account.domain.Account;
import ma.atlasbank.account.domain.AccountStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface AccountRepository {

    Account save(Account account);

    Optional<Account> findById(UUID id);

    Optional<Account> findByAccountNumber(String accountNumber);

    /**
     * Loads the account with an exclusive row lock held until the end of the transaction:
     * concurrent postings on the same account are serialized by the database.
     */
    Optional<Account> lockByAccountNumber(String accountNumber);

    List<Account> findAllByCustomerIdAndStatus(UUID customerId, AccountStatus status);

    Page<Account> search(AccountSearchCriteria criteria, Pageable pageable);
}
