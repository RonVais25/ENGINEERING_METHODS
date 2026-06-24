package server.dao;

import common.dto.CancellationsReportDTO;
import common.dto.CancellationsReportRow;
import common.dto.TotalVisitorsReportDTO;
import common.dto.TotalVisitorsReportRow;
import common.dto.UsageReportDTO;
import common.dto.UsageReportRow;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
     * Usage report: a single park's per-day <em>peak concurrent occupancy</em>
     * over a date range, paired with the park's {@code max_capacity} so the client
     * can plot each day against the "full" line and reveal under-full days.
     *
     * <p>Unlike the two department-manager reports this one is always for one
     * specific park (the {@code parkId} is required, never {@code null}) — the park
     * manager's own park, resolved by the controller from their session. The park's
     * {@code name} and {@code max_capacity} are read first; an unknown park id
     * yields a {@code null} return.
     *
     * <p><strong>The metric.</strong> "Peak concurrent occupancy" for a day is the
     * high-water mark of people physically inside the park at any instant that day —
     * the largest sum of headcounts of visits overlapping a single moment. Because
     * occupancy only ever <em>rises</em> at an entry, that maximum is always reached
     * at the moment some visit enters; so for every visit that entered on the day we
     * compute the occupancy at its entry instant — the sum of headcounts of all
     * visits already inside then ({@code entered_at <= that instant} and either still
     * open or {@code exited_at > that instant}) — and take the day's largest. A visit
     * still inside from a previous day is counted, which is what concurrency means.
     * This is computed in SQL: the inner correlated sum gives the occupancy at each
     * entry instant, and {@code MAX(...) GROUP BY day} reduces it to one peak per day.
     *
     * <p>The query only returns days that had at least one entry. The caller-facing
     * series, however, must be continuous, so every calendar day of the inclusive
     * range is then walked and any day the query did not report is zero-filled — a
     * day with no visits shows a peak of 0, and a range with no visits at all is a
     * flat zero series (not an error).
     *
     * @param from   inclusive range start, ISO {@code yyyy-MM-dd}
     * @param to     inclusive range end, ISO {@code yyyy-MM-dd}
     * @param parkId the park to report on (required — the manager's own park)
     * @return the populated {@link UsageReportDTO}, or {@code null} if the park is
     *         unknown or the query fails
     */
    public UsageReportDTO usage(String from, String to, int parkId) {
        String parkSql = "SELECT name, max_capacity FROM park WHERE id = ?";

        // For each entry on an in-range day, the inner correlated subquery sums the
        // headcounts of all visits overlapping that entry instant (the occupancy at
        // that moment); the outer MAX reduces those to the day's peak.
        String peakSql =
                "SELECT occ.day AS d, MAX(occ.concurrent) AS peak " +
                "FROM ( " +
                "    SELECT DATE(v1.entered_at) AS day, " +
                "           (SELECT COALESCE(SUM(v2.headcount), 0) " +
                "              FROM visit v2 " +
                "             WHERE v2.park_id = v1.park_id " +
                "               AND v2.entered_at <= v1.entered_at " +
                "               AND (v2.exited_at IS NULL OR v2.exited_at > v1.entered_at) " +
                "           ) AS concurrent " +
                "      FROM visit v1 " +
                "     WHERE v1.park_id = ? " +
                "       AND DATE(v1.entered_at) BETWEEN ? AND ? " +
                ") AS occ " +
                "GROUP BY occ.day";

        String parkName = null;
        int maxCapacity = 0;
        // Peaks keyed by ISO day string ("yyyy-MM-dd"), only for days with visits.
        Map<String, Integer> peaks = new HashMap<>();

        try (Connection conn = DBConnection.getConnection()) {

            try (PreparedStatement stmt = conn.prepareStatement(parkSql)) {
                stmt.setInt(1, parkId);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (!rs.next()) {
                        return null; // unknown park id
                    }
                    parkName = rs.getString("name");
                    maxCapacity = rs.getInt("max_capacity");
                }
            }

            try (PreparedStatement stmt = conn.prepareStatement(peakSql)) {
                stmt.setInt(1, parkId);
                stmt.setString(2, from);
                stmt.setString(3, to);
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        peaks.put(rs.getString("d"), rs.getInt("peak"));
                    }
                }
            }

        } catch (Exception e) {
            ServerLog.daoError(e);
            return null;
        }

        // Zero-fill: one row per calendar day of the inclusive range, oldest first,
        // reading the day's peak from the query (0 when the day had no visits).
        List<UsageReportRow> rows = new ArrayList<>();
        try {
            LocalDate start = LocalDate.parse(from);
            LocalDate end = LocalDate.parse(to);
            for (LocalDate d = start; !d.isAfter(end); d = d.plusDays(1)) {
                String key = d.toString(); // ISO yyyy-MM-dd, matching the SQL DATE() keys
                rows.add(new UsageReportRow(key, peaks.getOrDefault(key, 0)));
            }
        } catch (Exception e) {
            ServerLog.daoError(e);
            return null;
        }

        return new UsageReportDTO(from, to, parkId, parkName, maxCapacity, rows);
    }

    /**
     * Total-visitors-by-type report: a single park's total visitor headcount over a
     * date range, split into the two reporting categories — individuals
     * ({@code INDIVIDUAL + FAMILY}) versus organized groups ({@code GROUP}).
     *
     * <p>Like {@link #usage} this is always for one specific park (the {@code parkId}
     * is required, never {@code null}) — the park manager's own park, resolved by the
     * controller from their session. The park's {@code name} is read first; an unknown
     * park id yields a {@code null} return.
     *
     * <p><strong>The metric is a headcount, not a visit count.</strong> The spec asks
     * for the <em>number of visitors</em>, so each category's figure is
     * {@code SUM(v.headcount)} — the total party size across the matching visits — not
     * {@code COUNT(*)} of the visits. One group visit of 30 people therefore counts as
     * 30 visitors, not 1. {@code COALESCE(SUM(...), 0)} keeps an empty category at 0
     * rather than SQL {@code NULL}.
     *
     * <p>As in {@link #visitsByType}, the source is {@code visit v LEFT JOIN
     * reservation r} so a visit's category comes from its reservation when it has one
     * and from the visit's own {@code visit_type} when it is a casual walk-in
     * ({@code reservation_id IS NULL}) — that is the {@code COALESCE} on {@code visit_type}.
     * Visits are filtered by {@code DATE(entered_at)} falling within {@code [from, to]}
     * inclusive. Results are grouped into the two reporting categories; a category with
     * no visitors in the window is zero-filled so the DTO always returns both, in a
     * stable order — an empty range is a pair of zeros, not an error.
     *
     * @param from   inclusive range start, ISO {@code yyyy-MM-dd}
     * @param to     inclusive range end, ISO {@code yyyy-MM-dd}
     * @param parkId the park to report on (required — the manager's own park)
     * @return the populated {@link TotalVisitorsReportDTO}, or {@code null} if the park
     *         is unknown or the query fails
     */
    public TotalVisitorsReportDTO totalVisitorsByType(String from, String to, int parkId) {
        String parkSql = "SELECT name FROM park WHERE id = ?";

        // Headcount (number of visitors), not COUNT(*) (number of visits): SUM the
        // party size of every matching visit, collapsed into the two categories.
        String visitorsSql =
                "SELECT CASE WHEN COALESCE(r.visit_type, v.visit_type) = 'GROUP' " +
                "            THEN 'GROUPS' ELSE 'INDIVIDUALS' END AS category, " +
                "       COALESCE(SUM(v.headcount), 0) AS visitor_count " +
                "FROM visit v " +
                "LEFT JOIN reservation r ON v.reservation_id = r.id " +
                "WHERE v.park_id = ? " +
                "  AND DATE(v.entered_at) BETWEEN ? AND ? " +
                "GROUP BY category";

        String parkName = null;
        // Zero-filled defaults; overwritten by whatever the query returns. Using a
        // fixed two-slot result guarantees both categories appear, in a stable order.
        int individualsCount = 0;
        int groupsCount = 0;

        try (Connection conn = DBConnection.getConnection()) {

            try (PreparedStatement stmt = conn.prepareStatement(parkSql)) {
                stmt.setInt(1, parkId);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (!rs.next()) {
                        return null; // unknown park id
                    }
                    parkName = rs.getString("name");
                }
            }

            try (PreparedStatement stmt = conn.prepareStatement(visitorsSql)) {
                stmt.setInt(1, parkId);
                stmt.setString(2, from);
                stmt.setString(3, to);
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        String category = rs.getString("category");
                        int count = rs.getInt("visitor_count");
                        if ("GROUPS".equals(category)) {
                            groupsCount = count;
                        } else {
                            individualsCount = count;
                        }
                    }
                }
            }

        } catch (Exception e) {
            ServerLog.daoError(e);
            return null;
        }

        List<TotalVisitorsReportRow> rows = new ArrayList<>();
        rows.add(new TotalVisitorsReportRow("INDIVIDUALS", individualsCount));
        rows.add(new TotalVisitorsReportRow("GROUPS", groupsCount));
        return new TotalVisitorsReportDTO(from, to, parkId, parkName, rows);
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
