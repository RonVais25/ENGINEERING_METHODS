package common.dto;

import java.io.Serializable;
import java.util.List;

/**
 * Result of the Usage Report: park/day rows where the park did not reach full
 * capacity in the selected range.
 */
public class UsageReportDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String fromDate;
    private final String toDate;
    private final Integer parkId;
    private final List<UsageReportRow> rows;

    /**
     * Creates a populated usage report.
     *
     * @param fromDate inclusive range start, ISO {@code yyyy-MM-dd}
     * @param toDate   inclusive range end, ISO {@code yyyy-MM-dd}
     * @param parkId   park filter, or {@code null} for all parks
     * @param rows     under-capacity rows
     */
    public UsageReportDTO(String fromDate, String toDate, Integer parkId, List<UsageReportRow> rows) {
        this.fromDate = fromDate;
        this.toDate = toDate;
        this.parkId = parkId;
        this.rows = rows;
    }

    public String getFromDate() { return fromDate; }
    public String getToDate() { return toDate; }
    public Integer getParkId() { return parkId; }
    public List<UsageReportRow> getRows() { return rows; }
}
