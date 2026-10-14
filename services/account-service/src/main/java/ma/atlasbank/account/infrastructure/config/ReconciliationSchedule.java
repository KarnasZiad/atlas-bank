package ma.atlasbank.account.infrastructure.config;

import io.micrometer.core.instrument.MeterRegistry;
import java.util.concurrent.atomic.AtomicInteger;
import ma.atlasbank.account.application.LedgerReconciliation;
import ma.atlasbank.account.application.view.ReconciliationReport;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * Reconciles the ledger periodically and exposes the result as a gauge
 * ({@code atlas.ledger.discrepancies}), so that an alert fires on any value above zero.
 */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
class ReconciliationSchedule {

    private final LedgerReconciliation reconciliation;
    private final AtomicInteger discrepancies;

    ReconciliationSchedule(LedgerReconciliation reconciliation, MeterRegistry meters) {
        this.reconciliation = reconciliation;
        this.discrepancies = meters.gauge("atlas.ledger.discrepancies", new AtomicInteger());
    }

    @Scheduled(cron = "${atlas.accounts.reconciliation.cron}", zone = "${atlas.platform.zone-id:Africa/Casablanca}")
    void reconcile() {
        ReconciliationReport report = reconciliation.reconcile();
        discrepancies.set(report.accountsNotMatchingTheirEntries().size() + report.unbalancedTransactions().size());
    }
}
