package server.scheduler;

import java.util.function.Consumer;

import server.control.ReservationController;

/**
 * Sweeps expired waiting-list grab offers. Each poll it asks
 * {@link ReservationController#expireOverdueOffers()} to remove every offer whose
 * window has lapsed, cancel the forfeited WAITING reservation, and advance the
 * grab to the next eligible party. The grab window itself is
 * {@link SchedulerConfig#getGrabWindowMinutes()} — shrink it in {@code .env} to
 * make the forfeit visible during the demo.
 */
public class WaitlistGrabExpiryJob implements SchedulerJob {

    /** Registry key / manual-trigger handle for this job. */
    public static final String NAME = "waitlist-grab-expiry";

    /** Stateless controller collaborator (holds only DAOs); a fresh instance is fine. */
    private final ReservationController reservations = new ReservationController();

    /** One-line summary sink, wired to the server console activity log. */
    private final Consumer<String> log;
/**
 * Creates a new waitlist grab expiry job instance.
 * @param log value supplied to the operation
 */

    public WaitlistGrabExpiryJob(Consumer<String> log) {
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
        // Forced (manual "run now"): expire any currently-active grab offer now,
        // skipping the grab-window wait; non-forced keeps the window.
        int expired = reservations.expireOverdueOffers(force);
        if (expired > 0) {
            log.accept("[scheduler] " + NAME + ": expired " + expired
                    + " grab offer(s) — forfeited reservation(s) cancelled, queue advanced");
        }
    }
}
