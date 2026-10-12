package ma.atlasbank.account.infrastructure.persistence;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import ma.atlasbank.account.application.port.LedgerEntryRepository;
import ma.atlasbank.account.domain.EntryDirection;
import ma.atlasbank.account.domain.LedgerEntry;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

interface JpaLedgerEntryRepository extends JpaRepository<LedgerEntry, UUID>, LedgerEntryRepository {

    Page<LedgerEntry> findByAccountIdAndBookedAtGreaterThanEqualAndBookedAtLessThan(
            UUID accountId, Instant from, Instant to, Pageable pageable);

    @Query("""
            select coalesce(sum(entry.amount), 0) from LedgerEntry entry
            where entry.accountId = :accountId and entry.direction = :direction and entry.bookedAt >= :since
            """)
    BigDecimal sumOfAmounts(UUID accountId, EntryDirection direction, Instant since);

    @Override
    default Page<LedgerEntry> findStatement(UUID accountId, Instant from, Instant to, Pageable pageable) {
        return findByAccountIdAndBookedAtGreaterThanEqualAndBookedAtLessThan(accountId, from, to, pageable);
    }

    @Override
    default BigDecimal sumOfDebitsSince(UUID accountId, Instant since) {
        return sumOfAmounts(accountId, EntryDirection.DEBIT, since);
    }
}
