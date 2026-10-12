package ma.atlasbank.account.application.port;

import java.util.Optional;
import java.util.UUID;
import ma.atlasbank.account.domain.AccountHolder;

public interface AccountHolderRepository {

    AccountHolder save(AccountHolder holder);

    Optional<AccountHolder> findById(UUID customerId);
}
