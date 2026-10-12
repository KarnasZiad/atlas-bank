package ma.atlasbank.account.infrastructure.persistence;

import java.util.UUID;
import ma.atlasbank.account.application.port.AccountHolderRepository;
import ma.atlasbank.account.domain.AccountHolder;
import org.springframework.data.jpa.repository.JpaRepository;

interface JpaAccountHolderRepository extends JpaRepository<AccountHolder, UUID>, AccountHolderRepository {
}
