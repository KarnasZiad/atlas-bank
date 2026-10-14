package ma.atlasbank.transaction.infrastructure.config;

import io.micrometer.core.instrument.MeterRegistry;
import java.util.concurrent.atomic.AtomicLong;
import ma.atlasbank.transaction.application.OrderMaintenance;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * Runs the order book housekeeping and exposes the orders waiting too long for the ledger as a gauge
 * ({@code atlas.orders.stuck}), so that an alert fires on any value above zero.
 */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
class OrderMaintenanceSchedule {

    private final OrderMaintenance maintenance;
    private final AtomicLong stuck;

    OrderMaintenanceSchedule(OrderMaintenance maintenance, MeterRegistry meters) {
        this.maintenance = maintenance;
        this.stuck = meters.gauge("atlas.orders.stuck", new AtomicLong());
    }

    @Scheduled(cron = "${atlas.transactions.maintenance.cron:0 */5 * * * *}", zone = "${atlas.platform.zone-id:Africa/Casablanca}")
    void run() {
        maintenance.expireUnreviewedOrders();
        stuck.set(maintenance.countStuckOrders());
    }
}
