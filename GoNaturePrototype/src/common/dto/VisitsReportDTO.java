package common.dto;

import java.io.Serializable;
import java.util.List;

/**
 * Result of the Visits-by-Type report: per-category visit counts and average
 * stay lengths over a date range, optionally narrowed to one park.
 *
 * <p>The {@code fromDate}/{@code toDate}/{@code parkId} fields echo the filter the
 * report was run with so the client can label the output without re-deriving it;
 * {@code parkId} is {@code null} when the report spans the whole region (all
 * parks). {@code rows} always carries both categories — {@code "INDIVIDUALS"} and
 * {@code "GROUPS"} — even when one had no visits in the window (zero-filled).
 *
 * <p>Built server-side by {@link server.dao.ReportDAO#visitsByType} in response to
 * {@link RequestType#REPORT_VISITS_BY_TYPE}; immutable and {@link Serializable}.
 */
public class VisitsReportDTO implements Serializable {
/** Serialization identifier for this class. */
    private static final long serialVersionUID = 1L;

    /** Echo of the inclusive range start, ISO {@code yyyy-MM-dd}. */
    private final String fromDate;
    /** Echo of the inclusive range end, ISO {@code yyyy-MM-dd}. */
    private final String toDate;
    /** Echo of the park filter, or {@code null} for the whole region (all parks). */
    private final Integer parkId;
    /** One row per visitor category (always both {@code "INDIVIDUALS"} and {@code "GROUPS"}). */
    private final List<VisitsReportRow> rows;

    /**
     * Creates a fully populated visits report.
     *
     * @param fromDate inclusive range start, ISO {@code yyyy-MM-dd}
     * @param toDate   inclusive range end, ISO {@code yyyy-MM-dd}
     * @param parkId   park filter, or {@code null} for the whole region
     * @param rows     the per-category rows (both categories, zero-filled as needed)
     */
    public VisitsReportDTO(String fromDate, String toDate, Integer parkId, List<VisitsReportRow> rows) {
        this.fromDate = fromDate;
        this.toDate = toDate;
        this.parkId = parkId;
        this.rows = rows;
    }

    /** @return the inclusive range start, ISO {@code yyyy-MM-dd} */
    public String getFromDate() {
        return fromDate;
    }

    /** @return the inclusive range end, ISO {@code yyyy-MM-dd} */
    public String getToDate() {
        return toDate;
    }

    /** @return the park filter, or {@code null} for the whole region (all parks) */
    public Integer getParkId() {
        return parkId;
    }

    /** @return the per-category rows (both {@code "INDIVIDUALS"} and {@code "GROUPS"}) */
    public List<VisitsReportRow> getRows() {
        return rows;
    }
}
