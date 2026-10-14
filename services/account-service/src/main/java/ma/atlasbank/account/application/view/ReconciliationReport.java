package ma.atlasbank.account.application.view;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Result of a consistency check of the books.
 *
 * @param accountsNotMatchingTheirEntries accounts whose balance is not the sum of their entries
 * @param unbalancedTransactions          transactions whose debits and credits differ
 */
public record ReconciliationReport(
        Instant checkedAt,
        List<String> accountsNotMatchingTheirEntries,
        List<UUID> unbalancedTransactions) {

    public boolean isBalanced() {
        return accountsNotMatchingTheirEntries.isEmpty() && unbalancedTransactions.isEmpty();
    }
}
