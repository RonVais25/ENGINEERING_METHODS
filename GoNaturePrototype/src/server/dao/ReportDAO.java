package server.dao;

import common.dto.CancellationsReportDTO;
import common.dto.CancellationsReportRow;
import common.dto.VisitsReportDTO;
import common.dto.VisitsReportRow;
import server.db.DBConnection;
import server.util.ServerLog;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * Read-only data access object backing the department manager's reports.
 *
 * <p>Follows the same conventions as the other DAOs: each method opens a
 * short-lived {@link java.sql.Connection} from {@link server.db.DBConnection},
 * runs parameterized statements, and maps rows into report DTOs. SQL exceptions
 * are logged and surfaced as a {@code null} return rather than propagated, so the
 * controller signals failure through the return value.
 *
 * <p>Both reports take an optional park filter. When a {@code parkId} is supplied
 * it is appended as a parameterized {@code AND ... = ?} clause; when it is
 * {@code null} the clause is omitted entirely so the query spans the whole region.
 * Building the clause this way (never string-concatenating the value itself) keeps
 * the statement injection-safe whether or not a park is given.
 */
public class ReportDAO {

    /** Creates the report DAO. */
    public ReportDAO() { }

    /**
     * Visits-by-type report: per-category visit counts and average stay lengths
     * over a date range, optionally narrowed to a single park.
     *
     * <p>The source is {@code visit v LEFT JOIN reservation r} so a visit's
     * category can be read from its reservation when it has one and from the
     * visit's own {@code visit_type} when it is a casual walk-in
     * ({@code reservation_id IS NULL}) — that is what the {@code COALESCE} does.
     * Visits are filtered by {@code DATE(entered_at)} falling within
     * {@code [from, to]} inclusive.
     *
     * <p>{@code visitCount} is {@code COUNT(*)} over every matching visit, but the
     * average stay is {@code AVG(TIMESTAMPDIFF(MINUTE, entered_at, exited_at))}:
     * for an open visit {@code exited_at} is {@code NULL}, the {@code TIMESTAMPDIFF}
     * is {@code NULL}, and SQL's {@code AVG} skips {@code NULL}s — so open visits
     * count toward the total but not toward the average, with no extra filter.
     * Results are grouped into the two reporting categories; a category with no
     * visits in the window is zero-filled so the DTO always returns both.
     *
     * @param from   inclusive range start, ISO {@code yyyy-MM-dd}
     * @param to     inclusive range end, ISO {@code yyyy-MM-dd}
     * @param parkId a specific park, or {@code null} for the whole region (all parks)
     * @return the populated {@link VisitsReportDTO}, or {@code null} if the query fails
     */
    public VisitsReportDTO visitsByType(String from, String to, Integer parkId) {
        StringBuilder sql = new StringBuilder(
                "SELECT CASE WHEN COALESCE(r.visit_type, v.visit_type) = 'GROUP' " +
                "            THEN 'GROUPS' ELSE 'INDIVIDUALS' END AS category, " +
                "       COUNT(*) AS visit_count, " +
                "       AVG(TIMESTAMPDIFF(MINUTE, v.entered_at, v.exited_at)) AS avg_stay " +
                "FROM visit v " +
                "LEFT JOIN reservation r ON v.reservation_id = r.id " +
                "WHERE DATE(v.entered_at) BETWEEN ? AND ? ");
        if (parkId != null) {
            sql.append("AND v.park_id = ? ");
        }
        sql.append("GROUP BY category");

        // Zero-filled defaults; overwritten by whatever the query returns. Using a
        // fixed two-slot result guarantees both categories appear, in a stable order.
        int individualsCount = 0;
        double individualsAvg = 0.0;
        int groupsCount = 0;
        double groupsAvg = 0.0;

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql.toString())) {

            int idx = 1;
            stmt.setString(idx++, from);
            stmt.setString(idx++, to);
            if (parkId != null) {
                stmt.setInt(idx++, parkId);
            }

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    String category = rs.getString("category");
                    int count = rs.getInt("visit_count");
                    // avg_stay is NULL when no visit in the category has exited yet.
                    double avg = rs.getDouble("avg_stay");
                    if (rs.wasNull()) {
                        avg = 0.0;
                    }
                    if ("GROUPS".equals(category)) {
                        groupsCount = count;
                        groupsAvg = avg;
                    } else {
                        individualsCount = count;
                        individualsAvg = avg;
                    }
                }
            }

        } catch (Exception e) {
            ServerLog.daoError(e);
            return null;
        }

        List<VisitsReportRow> rows = new ArrayList<>();
        rows.add(new VisitsReportRow("INDIVIDUALS", individualsCount, individualsAvg));
        rows.add(new VisitsReportRow("GROUPS", groupsCount, groupsAvg));
        return new VisitsReportDTO(from, to, parkId, rows);
    }

    /**
     * Cancellations report: a per-day breakdown of cancelled and no-show
     * reservations over a date range, optionally narrowed to a single park, with
     * range totals and a daily average.
     *
     * <p>The source is the {@code reservation} table filtered to rows whose status
     * is {@code CANCELLED} or {@code NO_SHOW} and whose {@code status_changed_at}
     * (the moment the cancellation/no-show was recorded) falls within
     * {@code [from, to]} inclusive. Rows are grouped by
     * {@code DATE(status_changed_at)} and the two statuses are split into separate
     * counts per day; only days with activity produce a row.
     *
     * <p>{@code avgPerDay} divides the grand total ({@code cancelled + no-show})
     * by the number of calendar days the inclusive range spans —
     * {@code DAYS.between(from, to) + 1}, floored at 1 to avoid division by zero on
     * a degenerate range. Quiet days therefore count as zero in the denominator,
     * giving the average daily load across the whole window rather than only across
     * the days that happened to have cancellations.
     *
     * @param from   inclusive range start, ISO {@code yyyy-MM-dd}
     * @param to     inclusive range end, ISO {@code yyyy-MM-dd}
     * @param parkId a specific park, or {@code null} for the whole region (all parks)
     * @return the populated {@link CancellationsReportDTO}, or {@code null} if the query fails
     */
    public CancellationsReportDTO cancellations(String from, String to, Integer parkId) {
        StringBuilder sql = new StringBuilder(
                "SELECT DATE(status_changed_at) AS d, " +
                "       SUM(status = 'CANCELLED') AS cancelled, " +
                "       SUM(status = 'NO_SHOW')   AS no_show " +
                "FROM reservation " +
                "WHERE status IN ('CANCELLED', 'NO_SHOW') " +
                "  AND DATE(status_changed_at) BETWEEN ? AND ? ");
        if (parkId != null) {
            sql.append("AND park_id = ? ");
        }
        sql.append("GROUP BY DATE(status_changed_at) ORDER BY d ASC");

        List<CancellationsReportRow> rows = new ArrayList<>();
        int totalCancelled = 0;
        int totalNoShow = 0;

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql.toString())) {

            int idx = 1;
            stmt.setString(idx++, from);
            stmt.setString(idx++, to);
            if (parkId != null) {
                stmt.setInt(idx++, parkId);
            }

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    String date = rs.getString("d");
                    int cancelled = rs.getInt("cancelled");
                    int noShow = rs.getInt("no_show");
                    rows.add(new CancellationsReportRow(date, cancelled, noShow));
                    totalCancelled += cancelled;
                    totalNoShow += noShow;
                }
            }

        } catch (Exception e) {
            ServerLog.daoError(e);
            return null;
        }

        long days = daysInRange(from, to);
        double avgPerDay = (totalCancelled + totalNoShow) / (double) days;
        return new CancellationsReportDTO(from, to, parkId, rows,
                totalCancelled, totalNoShow, avgPerDay);
    }

    /**
     * Number of calendar days the inclusive range {@code [from, to]} spans, used as
     * the {@code avgPerDay} denominator. {@code from == to} is one day; the result
     * is floored at 1 so a malformed or reversed range never divides by zero.
     *
     * @param from inclusive range start, ISO {@code yyyy-MM-dd}
     * @param to   inclusive range end, ISO {@code yyyy-MM-dd}
     * @return the number of days spanned, at least 1
     */
    private long daysInRange(String from, String to) {
        try {
            long span = ChronoUnit.DAYS.between(LocalDate.parse(from), LocalDate.parse(to)) + 1;
            return span < 1 ? 1 : span;
        } catch (Exception e) {
            return 1;
        }
    }
}
