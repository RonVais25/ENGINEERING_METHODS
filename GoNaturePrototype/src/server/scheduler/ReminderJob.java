package server.scheduler;

import java.util.List;
import java.util.function.Consumer;

import common.dto.ReservationDTO;
import server.control.NotificationService;
import server.dao.ReservationDAO;

/**
 * Sends confirmation reminders for upcoming PENDING reservations. Each poll it asks
 * {@link ReservationDAO#findReminderCandidates(int)} for PENDING bookings whose
 * visit falls within {@link SchedulerConfig#getReminderLeadHours()} and that have
 * not yet been reminded, sends each visitor a reminder asking them to confirm
 * within {@link SchedulerConfig#getConfirmTimeoutMinutes()} minutes, then stamps
 * {@code reminder_sent_at} so the reminder is not re-sent on the next poll.
 *
 * <p>This is the first half of the reminder→confirm-timeout pair: it starts the
 * confirmation clock, and {@link ConfirmTimeoutJob} enforces its deadline. The
 * {@code reminder_sent_at} stamp links the two — a reservation only becomes a
 * timeout candidate once this job has reminded it.
 */
public class ReminderJob implements SchedulerJob {

    /** Registry key / manual-trigger handle for this job. */
    public static final String NAME = "reminder";

    /** Stateless DAO collaborator, shared across runs. */
    private final ReservationDAO reservationDao = new ReservationDAO();

    /** Stateless notification helper, used to deliver the reminder. */
    private final NotificationService notifications = new NotificationService();

    /** One-line summary sink, wired to the server console activity log. */
    private final Consumer<String> log;
/**
 * Creates a new reminder job instance.
 * @param log value supplied to the operation
 */

    public ReminderJob(Consumer<String> log) {
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
        int leadHours = SchedulerConfig.getReminderLeadHours();
        int confirmMinutes = SchedulerConfig.getConfirmTimeoutMinutes();
        // Forced (manual "run now"): remind every upcoming un-reminded PENDING
        // reservation regardless of the lead-hours window; non-forced keeps it.
        List<ReservationDTO> due = reservationDao.findReminderCandidates(leadHours, force);
        int sent = 0;
        for (ReservationDTO r : due) {
            notifications.send(r.getVisitorId(), null, "SIM_EMAIL",
                    "Reminder: your visit is on " + r.getVisitDate()
                            + ". Please confirm within " + confirmMinutes
                            + " minutes or it will be cancelled.");
            // Stamp after sending so the guard only marks reservations we reminded;
            // this is what prevents the reminder firing again on every poll.
            reservationDao.markReminderSent(r.getId());
            sent++;
        }
        if (sent > 0) {
            log.accept("[scheduler] " + NAME + ": sent " + sent + " confirmation reminder(s)");
        }
    }
}
