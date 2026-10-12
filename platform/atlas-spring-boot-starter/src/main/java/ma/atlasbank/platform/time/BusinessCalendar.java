package ma.atlasbank.platform.time;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

/**
 * Business time of the bank: "today" and day boundaries are evaluated in the bank's time zone,
 * not in the server's, and always from the injected clock.
 */
public class BusinessCalendar {

    private final Clock clock;
    private final ZoneId zone;

    public BusinessCalendar(Clock clock, ZoneId zone) {
        this.clock = clock;
        this.zone = zone;
    }

    public Instant now() {
        return Instant.now(clock);
    }

    public LocalDate today() {
        return LocalDate.now(clock.withZone(zone));
    }

    public Instant startOf(LocalDate day) {
        return day.atStartOfDay(zone).toInstant();
    }

    /** Exclusive upper bound of the given day. */
    public Instant endOf(LocalDate day) {
        return startOf(day.plusDays(1));
    }

    public Instant startOfToday() {
        return startOf(today());
    }
}
