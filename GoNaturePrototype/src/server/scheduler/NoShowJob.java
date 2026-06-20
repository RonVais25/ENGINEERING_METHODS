package server.scheduler;

import java.util.List;
import java.util.function.Consumer;

import common.dto.ReservationDTO;
import common.dto.ReservationStatus;
import server.control.NotificationService;
import server.dao.ReservationDAO;

/**
 * Marks past CONFIRMED reservations that nobody ever entered the park for as
 * {@link ReservationStatus#NO_SHOW}. Each poll it asks
 * {@link ReservationDAO#findNoShowCandidates()} for reservations whose visit date
 * is past and that have no {@code visit} row, flips each to NO_SHOW (which stamps
 * {@code status_changed_at}), and sends the visitor a courtesy notification.
 */
public class NoShowJob implements SchedulerJob {

    /** Registry key / manual-trigger handle for this job. */
    public static final String NAME = "no-show";

    /** Stateless DAO collaborator, shared across runs. */
    private final ReservationDAO reservationDao = new ReservationDAO();

    /** Stateless notification helper, used for the courtesy no-show notice. */
    private final NotificationService notifications = new NotificationService();

    /** One-line summary sink, wired to the server console activity log. */
    private final Consumer<String> log;
/**
 * Creates a new no show job instance.
 * @param log value supplied to the operation
 */

    public NoShowJob(Consumer<String> log) {
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
        // Forced (manual "run now"): include today's past-due CONFIRMED bookings,
        // not just strictly-before-today; non-forced keeps the strict past check.
        List<ReservationDTO> candidates = reservationDao.findNoShowCandidates(force);
        int marked = 0;
        for (ReservationDTO r : candidates) {
            if (reservationDao.updateStatus(r.getId(), ReservationStatus.NO_SHOW)) {
                marked++;
                notifications.send(r.getVisitorId(), null, "SIM_EMAIL",
                        "You were marked as a no-show for reservation #" + r.getId()
                                + " (visit date " + r.getVisitDate() + ").");
            }
        }
        if (marked > 0) {
            log.accept("[scheduler] " + NAME + ": marked " + marked + " reservation(s) NO_SHOW");
        }
    }
}
