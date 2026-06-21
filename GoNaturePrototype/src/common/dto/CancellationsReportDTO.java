package common.dto;

import java.io.Serializable;
import java.util.List;

/**
 * Result of the Cancellations report: a per-day breakdown of cancelled and
 * no-show reservations over a date range, optionally narrowed to one park, with
 * range totals and a daily average.
 *
 * <p>The {@code fromDate}/{@code toDate}/{@code parkId} fields echo the filter the
 * report was run with; {@code parkId} is {@code null} when the report spans the
 * whole region (all parks). {@code rows} carries only the days that actually had
 * a cancellation or no-show (quiet days are omitted), ordered oldest first.
 *
 * <p>{@code totalCancelled} and {@code totalNoShow} sum the rows. {@code avgPerDay}
 * is {@code (totalCancelled + totalNoShow)} divided by the number of calendar days
 * spanned by the inclusive range {@code [fromDate, toDate]} — i.e. quiet days
 * count as zero in the denominator, giving the average daily cancellation load
 * across the whole window rather than across only the days with activity.
 *
 * <p>Built server-side by {@link server.dao.ReportDAO#cancellations} in response to
 * {@link RequestType#REPORT_CANCELLATIONS}; immutable and {@link Serializable}.
 */
public class CancellationsReportDTO implements Serializable {
    /** Serialization-format version identifier. */
    private static final long serialVersionUID = 1L;

    /** Echo of the inclusive range start, ISO {@code yyyy-MM-dd}. */
    private final String fromDate;
    /** Echo of the inclusive range end, ISO {@code yyyy-MM-dd}. */
    private final String toDate;
    /** Echo of the park filter, or {@code null} for the whole region (all parks). */
    private final Integer parkId;
    /** One row per active day, oldest first (quiet days omitted). */
    private final List<CancellationsReportRow> rows;
    /** Total reservations cancelled across the whole range. */
    private final int totalCancelled;
    /** Total reservations marked no-show across the whole range. */
    private final int totalNoShow;
    /** Average cancellations+no-shows per calendar day of the inclusive range. */
    private final double avgPerDay;

    /**
     * Creates a fully populated cancellations report.
     *
     * @param fromDate       inclusive range start, ISO {@code yyyy-MM-dd}
     * @param toDate         inclusive range end, ISO {@code yyyy-MM-dd}
     * @param parkId         park filter, or {@code null} for the whole region
     * @param rows           the per-day rows, oldest first (quiet days omitted)
     * @param totalCancelled total cancelled across the range
     * @param totalNoShow    total no-show across the range
     * @param avgPerDay      average cancellations+no-shows per calendar day of the range
     */
    public CancellationsReportDTO(String fromDate, String toDate, Integer parkId,
                                  List<CancellationsReportRow> rows,
                                  int totalCancelled, int totalNoShow, double avgPerDay) {
        this.fromDate = fromDate;
        this.toDate = toDate;
        this.parkId = parkId;
        this.rows = rows;
        this.totalCancelled = totalCancelled;
        this.totalNoShow = totalNoShow;
        this.avgPerDay = avgPerDay;
    }

    /** {@return the inclusive range start, ISO {@code yyyy-MM-dd}} */
    public String getFromDate() {
        return fromDate;
    }

    /** {@return the inclusive range end, ISO {@code yyyy-MM-dd}} */
    public String getToDate() {
        return toDate;
    }

    /** {@return the park filter, or {@code null} for the whole region (all parks)} */
    public Integer getParkId() {
        return parkId;
    }

    /** {@return the per-day rows, oldest first (quiet days omitted)} */
    public List<CancellationsReportRow> getRows() {
        return rows;
    }

    /** {@return total reservations cancelled across the whole range} */
    public int getTotalCancelled() {
        return totalCancelled;
    }

    /** {@return total reservations marked no-show across the whole range} */
    public int getTotalNoShow() {
        return totalNoShow;
    }

    /** {@return average cancellations+no-shows per calendar day of the inclusive range} */
    public double getAvgPerDay() {
        return avgPerDay;
    }
}
