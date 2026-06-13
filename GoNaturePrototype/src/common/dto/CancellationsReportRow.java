package common.dto;

import java.io.Serializable;

/**
 * One per-day row of the Cancellations report: how many reservations were
 * cancelled and how many were marked no-show on a single calendar day.
 *
 * <p>The day is the date a reservation's status last changed
 * ({@code DATE(status_changed_at)}), not its visit date — this report answers
 * "when did the cancellation/no-show happen", which is when the no-show sweep or
 * a staff/visitor cancellation stamped the row. Only days on which at least one
 * such event occurred appear; quiet days are simply absent from the list.
 *
 * <p>Built server-side by {@link server.dao.ReportDAO#cancellations} and shipped
 * inside a {@link CancellationsReportDTO}; immutable and {@link Serializable}.
 */
public class CancellationsReportRow implements Serializable {
    private static final long serialVersionUID = 1L;

    /** The calendar day the status changed, ISO {@code yyyy-MM-dd}. */
    private final String date;
    /** Reservations cancelled ({@link ReservationStatus#CANCELLED}) that day. */
    private final int cancelled;
    /** Reservations marked no-show ({@link ReservationStatus#NO_SHOW}) that day. */
    private final int noShow;

    /**
     * Creates a fully populated per-day row.
     *
     * @param date      the calendar day, ISO {@code yyyy-MM-dd}
     * @param cancelled reservations cancelled that day
     * @param noShow    reservations marked no-show that day
     */
    public CancellationsReportRow(String date, int cancelled, int noShow) {
        this.date = date;
        this.cancelled = cancelled;
        this.noShow = noShow;
    }

    /** @return the calendar day, ISO {@code yyyy-MM-dd} */
    public String getDate() {
        return date;
    }

    /** @return reservations cancelled that day */
    public int getCancelled() {
        return cancelled;
    }

    /** @return reservations marked no-show that day */
    public int getNoShow() {
        return noShow;
    }
}
