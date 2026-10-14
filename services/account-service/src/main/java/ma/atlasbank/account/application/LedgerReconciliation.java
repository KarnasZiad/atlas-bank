package ma.atlasbank.account.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ma.atlasbank.account.application.port.LedgerChecks;
import ma.atlasbank.account.application.view.ReconciliationReport;
import ma.atlasbank.platform.time.BusinessCalendar;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Proves the books are consistent: every balance equals the sum of its entries and every
 * transaction is balanced. Database constraints make a discrepancy very unlikely; this check is
 * what detects one if it happens anyway (manual intervention, bug, tampering).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LedgerReconciliation {

    private final LedgerChecks checks;
    private final BusinessCalendar calendar;

    @Transactional(readOnly = true)
    public ReconciliationReport reconcile() {
        ReconciliationReport report = new ReconciliationReport(
                calendar.now(), checks.accountsNotMatchingTheirEntries(), checks.unbalancedTransactions());
        if (report.isBalanced()) {
            log.info("Ledger reconciled: balances match their entries, every transaction is balanced");
        } else {
            log.error("LEDGER INCONSISTENCY: accounts {} do not match their entries, transactions {} are unbalanced",
                    report.accountsNotMatchingTheirEntries(), report.unbalancedTransactions());
        }
        return report;
    }
}
