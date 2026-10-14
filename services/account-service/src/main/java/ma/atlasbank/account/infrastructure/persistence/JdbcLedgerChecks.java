package ma.atlasbank.account.infrastructure.persistence;

import java.util.List;
import java.util.UUID;
import ma.atlasbank.account.application.port.LedgerChecks;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * Set-based checks run by PostgreSQL over the whole ledger (one scan each, no data loaded in memory).
 */
@Component
class JdbcLedgerChecks implements LedgerChecks {

    private final JdbcClient jdbc;

    JdbcLedgerChecks(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Customer balances increase with credits, the till with debits (cash coming in).
     */
    @Override
    public List<String> accountsNotMatchingTheirEntries() {
        return jdbc.sql("""
                        select a.account_number
                        from account a
                        left join (select account_id,
                                          sum(case when direction = 'CREDIT' then amount else -amount end) as net_credit
                                   from ledger_entry
                                   group by account_id) e on e.account_id = a.id
                        where a.balance <> case when a.account_type = 'TELLER_CASH' then -coalesce(e.net_credit, 0)
                                                else coalesce(e.net_credit, 0) end
                        order by a.account_number
                        """)
                .query(String.class)
                .list();
    }

    @Override
    public List<UUID> unbalancedTransactions() {
        return jdbc.sql("""
                        select transaction_id
                        from ledger_entry
                        group by transaction_id
                        having sum(case when direction = 'DEBIT' then amount else 0 end)
                            <> sum(case when direction = 'CREDIT' then amount else 0 end)
                        order by transaction_id
                        """)
                .query(UUID.class)
                .list();
    }
}
