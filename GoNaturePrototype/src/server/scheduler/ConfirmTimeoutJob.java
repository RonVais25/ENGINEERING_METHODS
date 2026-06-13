package server.scheduler;

import java.util.function.Consumer;

import server.control.ReservationController;

/**
 * Auto-cancels reminded PENDING reservations that were not confirmed within the
 * window. Each poll it asks
 * {@link ReservationController#expireUnconfirmedReservations()} to cancel every
 * PENDING booking whose {@link SchedulerConfig#getConfirmTimeoutMinutes()}-minute
 * confirmation window (started by {@link ReminderJob}) has lapsed, notify the
 * visitor, and offer the freed slot to the waiting list.
 *
 * <p>Second half of the reminder→confirm-timeout pair. It only ever touches
 * still-PENDING rows, so a visitor who confirmed in time is left untouched —
 * confirming clears the reservation out of the timeout sweep.
 */
public class ConfirmTimeoutJob implements SchedulerJob {

    /** Registry key / manual-trigger handle for this job. */
    public static final String NAME = "confirm-timeout";

    /** Stateless controller collaborator (holds only DAOs); a fresh instance is fine. */
    private final ReservationController reservations = new ReservationController();

    /** One-line summary sink, wired to the server console activity log. */
    private final Consumer<String> log;

    public ConfirmTimeoutJob(Consumer<String> log) {
        this.log = log;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public void runOnce() {
        int cancelled = reservations.expireUnconfirmedReservations();
        if (cancelled > 0) {
            log.accept("[scheduler] " + NAME + ": auto-cancelled " + cancelled
                    + " unconfirmed reservation(s) — visitor notified, slot offered to waitlist");
        }
    }
}
