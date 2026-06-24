package common.dto;

import java.io.Serializable;

/**
 * One day of the Usage report: a park's peak concurrent occupancy on a single
 * calendar day within the report's date range.
 *
 * <p>{@code peakOccupancy} is the high-water mark of people physically inside the
 * park that day — the largest sum of overlapping visit headcounts at any instant
 * (see {@link server.dao.ReportDAO#usage}). It is read against the park's
 * {@code maxCapacity} (carried on the enclosing {@link UsageReportDTO}) so a day
 * whose peak sits below capacity is visibly "not full". A day with no visits is
 * zero-filled ({@code peakOccupancy = 0}) so the series stays continuous across
 * the whole range rather than leaving a gap.
 *
 * <p>Built server-side by {@link server.dao.ReportDAO#usage} and shipped inside a
 * {@link UsageReportDTO}; immutable and {@link Serializable}.
 */
public class UsageReportRow implements Serializable {
    /** Serialization-format version identifier. */
    private static final long serialVersionUID = 1L;

    /** The calendar day this row reports on, ISO {@code yyyy-MM-dd}. */
    private final String date;
    /** Peak concurrent occupancy that day (sum of overlapping headcounts; 0 if no visits). */
    private final int peakOccupancy;

    /**
     * Creates a fully populated usage row.
     *
     * @param date          the calendar day, ISO {@code yyyy-MM-dd}
     * @param peakOccupancy the day's peak concurrent occupancy (0 if no visits)
     */
    public UsageReportRow(String date, int peakOccupancy) {
        this.date = date;
        this.peakOccupancy = peakOccupancy;
    }

    /** {@return the calendar day this row reports on, ISO {@code yyyy-MM-dd}} */
    public String getDate() {
        return date;
    }

    /** {@return the day's peak concurrent occupancy (0 if no visits)} */
    public int getPeakOccupancy() {
        return peakOccupancy;
    }
}
