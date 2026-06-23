package server.dao;

import common.dto.ChangeStatus;
import common.dto.PromotionDTO;
import server.db.DBConnection;
import server.util.ServerLog;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Data access object for the {@code promotion} table — the persistence behind the
 * park-promotion approval workflow.
 *
 * <p>Follows the same conventions as {@link ParkDAO} / {@link ParameterChangeDAO}:
 * each method opens a short-lived {@link java.sql.Connection} from
 * {@link server.db.DBConnection}, runs parameterized statements, and maps rows to
 * {@link PromotionDTO}. SQL exceptions are logged and surfaced as a
 * {@code null}/empty-list/{@code false}/{@code -1}/{@code 0} return rather than
 * being propagated, so callers signal failure through the return value.
 */
public class PromotionDAO {

    /** Creates the promotion DAO. */
    public PromotionDAO() { }

    /**
     * Columns selected by {@link #listByPark} and {@link #listPending}, joining the
     * park name and the definer's full name in for display. Order matches
     * {@link #map}. {@code defined_by} is a NOT NULL FK to {@code user.id}, so the
     * user join is a plain (inner) join — it never drops a promotion row.
     */
    private static final String SELECT_WITH_NAMES =
            "SELECT pr.id, pr.park_id, p.name AS park_name, pr.name, pr.discount_percent, " +
            "pr.start_date, pr.end_date, pr.status, pr.defined_by, " +
            "u.full_name AS defined_by_name, pr.approved_by, pr.created_at " +
            "FROM promotion pr " +
            "JOIN park p ON p.id = pr.park_id " +
            "JOIN `user` u ON u.id = pr.defined_by ";

    /**
     * Inserts a new promotion in the {@code PENDING} state, stamping
     * {@code created_at} with the current time. The park, name, percent, dates and
     * definer are taken from the supplied DTO; {@code status}/{@code approved_by}/
     * {@code decided_at} are left at their PENDING defaults.
     *
     * @param p the promotion to insert (its id is ignored — the DB assigns one)
     * @return the new auto-generated promotion id, or {@code -1} if the insert fails
     */
    public int insert(PromotionDTO p) {
        String sql = "INSERT INTO promotion " +
                "(park_id, name, discount_percent, start_date, end_date, status, defined_by, created_at) " +
                "VALUES (?, ?, ?, ?, ?, 'PENDING', ?, NOW())";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            stmt.setInt(1, p.getParkId());
            stmt.setString(2, p.getName());
            stmt.setInt(3, p.getDiscountPercent());
            stmt.setDate(4, Date.valueOf(p.getStartDate()));
            stmt.setDate(5, Date.valueOf(p.getEndDate()));
            stmt.setInt(6, p.getDefinedBy());

            if (stmt.executeUpdate() == 1) {
                try (ResultSet keys = stmt.getGeneratedKeys()) {
                    if (keys.next()) {
                        return keys.getInt(1);
                    }
                }
            }

        } catch (Exception e) {
            ServerLog.daoError(e);
        }

        return -1;
    }

    /**
     * Lists a single park manager's own promotions (every status), newest first.
     *
     * @param parkId the manager's park
     * @return that park's promotions (possibly empty); never {@code null}
     */
    public List<PromotionDTO> listByPark(int parkId) {
        String sql = SELECT_WITH_NAMES +
                "WHERE pr.park_id = ? ORDER BY pr.created_at DESC, pr.id DESC";
        List<PromotionDTO> result = new ArrayList<>();

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, parkId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    result.add(map(rs));
                }
            }

        } catch (Exception e) {
            ServerLog.daoError(e);
        }

        return result;
    }

    /**
     * Lists every promotion still awaiting a decision, across all parks, oldest first.
     *
     * @return the pending promotions (possibly empty); never {@code null}
     */
    public List<PromotionDTO> listPending() {
        String sql = SELECT_WITH_NAMES +
                "WHERE pr.status = 'PENDING' ORDER BY pr.created_at ASC, pr.id ASC";
        List<PromotionDTO> result = new ArrayList<>();

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                result.add(map(rs));
            }

        } catch (Exception e) {
            ServerLog.daoError(e);
        }

        return result;
    }

    /**
     * Looks up a single promotion by its primary key, with the park and definer
     * names joined in (so a caller can resolve the defining manager to notify).
     *
     * @param id the promotion identifier to fetch
     * @return the matching promotion, or {@code null} if no row matches or the query fails
     */
    public PromotionDTO getById(int id) {
        String sql = SELECT_WITH_NAMES + "WHERE pr.id = ?";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, id);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return map(rs);
                }
            }

        } catch (Exception e) {
            ServerLog.daoError(e);
        }

        return null;
    }

    /**
     * Approves a pending promotion, stamping {@code approved_by} and
     * {@code decided_at}. Guarded by {@code WHERE id = ? AND status = 'PENDING'} so
     * an already-decided promotion is left untouched and {@code false} is returned;
     * folding the guard into the {@code WHERE} clause makes it atomic.
     *
     * @param id         the promotion to approve
     * @param deptUserId the deciding department manager's {@code user.id}
     * @return {@code true} if the row was still PENDING and got approved
     */
    public boolean approve(int id, int deptUserId) {
        return decide(id, ChangeStatus.APPROVED, deptUserId);
    }

    /**
     * Rejects a pending promotion, stamping {@code approved_by} (the decider) and
     * {@code decided_at}. Same atomic PENDING guard as {@link #approve}.
     *
     * @param id         the promotion to reject
     * @param deptUserId the deciding department manager's {@code user.id}
     * @return {@code true} if the row was still PENDING and got rejected
     */
    public boolean reject(int id, int deptUserId) {
        return decide(id, ChangeStatus.REJECTED, deptUserId);
    }

    /**
     * Records a decision (approve/reject) on a promotion under the atomic PENDING
     * guard, stamping {@code approved_by} and {@code decided_at}.
     *
     * @param id         the promotion to decide
     * @param status     the decision to record ({@code APPROVED} or {@code REJECTED})
     * @param deptUserId the deciding department manager's {@code user.id}
     * @return {@code true} if the row was still PENDING and got updated
     */
    private boolean decide(int id, ChangeStatus status, int deptUserId) {
        String sql = "UPDATE promotion " +
                "SET status = ?, approved_by = ?, decided_at = NOW() " +
                "WHERE id = ? AND status = 'PENDING'";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, status.name());
            stmt.setInt(2, deptUserId);
            stmt.setInt(3, id);
            return stmt.executeUpdate() == 1;

        } catch (Exception e) {
            ServerLog.daoError(e);
        }

        return false;
    }

    /**
     * Returns the discount percentage of the active, approved promotion for a park
     * on a given date — an {@code APPROVED} promotion whose window contains the date
     * ({@code start_date <= date <= end_date}, both inclusive). If several overlap,
     * the highest percentage wins; if none apply, {@code 0}.
     *
     * <p>Kept deliberately self-contained (one parameterized query, no other state)
     * so it is cleanly callable in isolation and unit-testable.
     *
     * @param parkId the park to look up a promotion for
     * @param date   the visit date to test against each promotion's window
     * @return the highest active approved discount percent for that park/date, or {@code 0} if none
     */
    public int findActiveDiscountPercent(int parkId, LocalDate date) {
        String sql = "SELECT MAX(discount_percent) FROM promotion " +
                "WHERE park_id = ? AND status = 'APPROVED' " +
                "AND start_date <= ? AND end_date >= ?";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            Date sqlDate = Date.valueOf(date);
            stmt.setInt(1, parkId);
            stmt.setDate(2, sqlDate);
            stmt.setDate(3, sqlDate);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    int percent = rs.getInt(1);
                    // MAX over no matching rows yields SQL NULL -> getInt returns 0
                    // and wasNull() is true; treat that as "no active promotion".
                    return rs.wasNull() ? 0 : percent;
                }
            }

        } catch (Exception e) {
            ServerLog.daoError(e);
        }

        return 0;
    }

    /**
     * Maps the current {@link ResultSet} row to a {@link PromotionDTO}. Dates are
     * carried to the client as plain ISO strings (display-only / wire-friendly,
     * matching how {@code visit_date} travels elsewhere); {@code created_at} is a
     * display string too.
     *
     * @param rs a result set positioned on a joined promotion row
     * @return the mapped promotion
     * @throws SQLException if a column read fails
     */
    private PromotionDTO map(ResultSet rs) throws SQLException {
        int approvedRaw = rs.getInt("approved_by");
        Integer approvedBy = rs.wasNull() ? null : approvedRaw;

        Date start = rs.getDate("start_date");
        Date end = rs.getDate("end_date");
        Timestamp created = rs.getTimestamp("created_at");

        return new PromotionDTO(
                rs.getInt("id"),
                rs.getInt("park_id"),
                rs.getString("park_name"),
                rs.getString("name"),
                rs.getInt("discount_percent"),
                start == null ? null : start.toString(),
                end == null ? null : end.toString(),
                ChangeStatus.valueOf(rs.getString("status")),
                rs.getInt("defined_by"),
                rs.getString("defined_by_name"),
                approvedBy,
                created == null ? null : created.toString());
    }
}
