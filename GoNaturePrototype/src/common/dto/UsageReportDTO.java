package common.dto;

import java.io.Serializable;
import java.util.List;

/**
 * Result of the Usage report: a single park's per-day peak occupancy over a date
 * range, paired with the park's maximum capacity so under-full days are visible.
 *
 * <p>Unlike the department-manager reports ({@link VisitsReportDTO},
 * {@link CancellationsReportDTO}), the Usage report is run by a
 * {@code PARK_MANAGER} for their <em>own</em> park only — there is no park filter.
 * {@code parkId}/{@code parkName} therefore identify that one park, and
 * {@code maxCapacity} is the "full" line the daily peaks are read against.
 *
 * <p>{@code rows} carries one entry for <em>every</em> calendar day in the
 * inclusive range {@code [fromDate, toDate]}, oldest first — days with no visits
 * are zero-filled — so the occupancy series is continuous and a quiet day reads as
 * a 0 rather than a gap. A range with no visits at all is therefore a flat
 * baseline well below {@code maxCapacity}, not an empty result.
 *
 * <p>Built server-side by {@link server.dao.ReportDAO#usage} in response to
 * {@link RequestType#REPORT_USAGE}; immutable and {@link Serializable}.
 */
public class UsageReportDTO implements Serializable {
    /** Serialization-format version identifier. */
    private static final long serialVersionUID = 1L;

    /** Echo of the inclusive range start, ISO {@code yyyy-MM-dd}. */
    private final String fromDate;
    /** Echo of the inclusive range end, ISO {@code yyyy-MM-dd}. */
    private final String toDate;
    /** Identifier of the park this report covers (the manager's own park). */
    private final int parkId;
    /** Display name of the park this report covers. */
    private final String parkName;
    /** The park's maximum capacity — the "full" line the peaks are read against. */
    private final int maxCapacity;
    /** One row per calendar day of the inclusive range, oldest first (zero-filled). */
    private final List<UsageReportRow> rows;

    /**
     * Creates a fully populated usage report.
     *
     * @param fromDate    inclusive range start, ISO {@code yyyy-MM-dd}
     * @param toDate      inclusive range end, ISO {@code yyyy-MM-dd}
     * @param parkId      identifier of the park this report covers
     * @param parkName    display name of the park this report covers
     * @param maxCapacity the park's maximum capacity (the "full" line)
     * @param rows        one row per day of the range, oldest first (zero-filled)
     */
    public UsageReportDTO(String fromDate, String toDate, int parkId, String parkName,
                          int maxCapacity, List<UsageReportRow> rows) {
        this.fromDate = fromDate;
        this.toDate = toDate;
        this.parkId = parkId;
        this.parkName = parkName;
        this.maxCapacity = maxCapacity;
        this.rows = rows;
    }

    /** {@return the inclusive range start, ISO {@code yyyy-MM-dd}} */
    public String getFromDate() {
        return fromDate;
    }

    /** {@return the inclusive range end, ISO {@code yyyy-MM-dd}} */
    public String getToDate() {
        return toDate;
    }

    /** {@return the identifier of the park this report covers} */
    public int getParkId() {
        return parkId;
    }

    /** {@return the display name of the park this report covers} */
    public String getParkName() {
        return parkName;
    }

    /** {@return the park's maximum capacity (the "full" line the peaks are read against)} */
    public int getMaxCapacity() {
        return maxCapacity;
    }

    /** {@return one row per calendar day of the inclusive range, oldest first (zero-filled)} */
    public List<UsageReportRow> getRows() {
        return rows;
    }
}
