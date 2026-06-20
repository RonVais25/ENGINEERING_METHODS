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
/**
 * Creates a new confirm timeout job instance.
 * @param log value supplied to the operation
 */

    public ConfirmTimeoutJob(Consumer<String> log) {
        this.log = log;
    }
/**
 * Performs the name operation.
 * @return the result produced by the operation
 */

    @Override
    public String name() {
        return NAME;
    }
/**
 * Performs the run once operation.
 * @param force value supplied to the operation
 */

    @Override
    public void runOnce(boolean force) {
        // Forced (manual "run now"): cancel every still-PENDING reservation now,
        // skipping the reminder/confirm-timeout window; non-forced keeps it.
        int cancelled = reservations.expireUnconfirmedReservations(force);
        if (cancelled > 0) {
            log.accept("[scheduler] " + NAME + ": auto-cancelled " + cancelled
                    + " unconfirmed reservation(s) — visitor notified, slot offered to waitlist");
        }
    }
}
