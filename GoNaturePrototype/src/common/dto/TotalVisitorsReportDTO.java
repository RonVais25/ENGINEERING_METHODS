package common.dto;

import java.io.Serializable;
import java.util.List;

/**
 * Result of the Total-Visitors-by-Type report: a single park's total visitor
 * headcount over a date range, split into the two reporting categories.
 *
 * <p>Like the Usage report ({@link UsageReportDTO}) and unlike the
 * department-manager reports ({@link VisitsReportDTO},
 * {@link CancellationsReportDTO}), this report is run by a {@code PARK_MANAGER}
 * for their <em>own</em> park only — there is no park filter. {@code parkId} /
 * {@code parkName} therefore identify that one park, resolved server-side from
 * the manager's session.
 *
 * <p><strong>The metric is a headcount.</strong> Each row carries the
 * <em>number of visitors</em> (sum of party headcounts), not the number of
 * visits — see {@link TotalVisitorsReportRow}. {@code rows} always carries both
 * categories — {@code "INDIVIDUALS"} (INDIVIDUAL + FAMILY) and {@code "GROUPS"}
 * (GROUP) — even when one had no visitors in the window (zero-filled), so an
 * empty range is a pair of zeros rather than a missing bar or an error.
 *
 * <p>Built server-side by {@link server.dao.ReportDAO#totalVisitorsByType} in
 * response to {@link RequestType#REPORT_TOTAL_VISITORS}; immutable and
 * {@link Serializable}.
 */
public class TotalVisitorsReportDTO implements Serializable {
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
    /** One row per visitor category (always both {@code "INDIVIDUALS"} and {@code "GROUPS"}). */
    private final List<TotalVisitorsReportRow> rows;

    /**
     * Creates a fully populated total-visitors report.
     *
     * @param fromDate inclusive range start, ISO {@code yyyy-MM-dd}
     * @param toDate   inclusive range end, ISO {@code yyyy-MM-dd}
     * @param parkId   identifier of the park this report covers
     * @param parkName display name of the park this report covers
     * @param rows     the per-category rows (both categories, zero-filled as needed)
     */
    public TotalVisitorsReportDTO(String fromDate, String toDate, int parkId, String parkName,
                                  List<TotalVisitorsReportRow> rows) {
        this.fromDate = fromDate;
        this.toDate = toDate;
        this.parkId = parkId;
        this.parkName = parkName;
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

    /** {@return the per-category rows (both {@code "INDIVIDUALS"} and {@code "GROUPS"})} */
    public List<TotalVisitorsReportRow> getRows() {
        return rows;
    }
}
