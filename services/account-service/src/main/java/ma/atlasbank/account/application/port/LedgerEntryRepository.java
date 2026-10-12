package ma.atlasbank.account.application.port;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import ma.atlasbank.account.domain.LedgerEntry;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface LedgerEntryRepository {

    LedgerEntry save(LedgerEntry entry);

    /**
     * @param to exclusive upper bound
     */
    Page<LedgerEntry> findStatement(UUID accountId, Instant from, Instant to, Pageable pageable);

    BigDecimal sumOfDebitsSince(UUID accountId, Instant since);
}
