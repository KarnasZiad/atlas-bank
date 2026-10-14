package ma.atlasbank.account.application.port;

import java.util.List;
import java.util.UUID;

/**
 * Consistency checks of the books, computed by the database over the whole ledger.
 */
public interface LedgerChecks {

    /** Accounts whose balance differs from the sum of their ledger entries. */
    List<String> accountsNotMatchingTheirEntries();

    /** Transactions whose debits and credits do not add up to the same amount. */
    List<UUID> unbalancedTransactions();
}
