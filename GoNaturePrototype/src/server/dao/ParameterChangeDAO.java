package server.dao;

import common.dto.ChangeStatus;
import common.dto.ParamField;
import common.dto.ParameterChangeRequestDTO;
import server.db.DBConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

/**
 * Data access object for the {@code parameter_change_request} table — the
 * persistence behind the park-parameter approval workflow.
 *
 * <p>Follows the same conventions as {@link ReservationDAO}: each method opens a
 * short-lived {@link java.sql.Connection} from {@link server.db.DBConnection},
 * runs parameterized statements, and maps rows to
 * {@link ParameterChangeRequestDTO}. SQL exceptions are logged and surfaced as a
 * {@code null}/empty-list/{@code false}/{@code -1} return rather than being
 * propagated, so callers signal failure through the return value.
 */
public class ParameterChangeDAO {

    /**
     * Columns selected by {@link #listPending} and {@link #getById}, joining the
     * park name in for display. Order matches {@link #map}.
     */
    private static final String SELECT_WITH_PARK =
            "SELECT r.id, r.park_id, p.name AS park_name, r.requested_by, r.field, " +
            "r.old_value, r.new_value, r.status, r.decided_by, r.created_at, r.decided_at " +
            "FROM parameter_change_request r " +
            "JOIN park p ON p.id = r.park_id ";

    /**
     * Inserts a new change request in the {@code PENDING} state, stamping
     * {@code created_at} with the current time.
     *
     * @param parkId      the park the change targets
     * @param requestedBy the park manager's {@code user.id}
     * @param field       which parameter the change targets
     * @param oldValue    the park's current value for that field
     * @param newValue    the requested new value
     * @return the new auto-generated request id, or {@code -1} if the insert fails
     */
    public int insertRequest(int parkId, int requestedBy, ParamField field, int oldValue, int newValue) {
        String sql = "INSERT INTO parameter_change_request " +
                "(park_id, requested_by, field, old_value, new_value, status, created_at) " +
                "VALUES (?, ?, ?, ?, ?, 'PENDING', NOW())";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            stmt.setInt(1, parkId);
            stmt.setInt(2, requestedBy);
            stmt.setString(3, field.name());
            stmt.setInt(4, oldValue);
            stmt.setInt(5, newValue);

            if (stmt.executeUpdate() == 1) {
                try (ResultSet keys = stmt.getGeneratedKeys()) {
                    if (keys.next()) {
                        return keys.getInt(1);
                    }
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        return -1;
    }

    /**
     * Lists every request still awaiting a decision, oldest first.
     *
     * @return the pending requests (possibly empty); never {@code null}
     */
    public List<ParameterChangeRequestDTO> listPending() {
        String sql = SELECT_WITH_PARK +
                "WHERE r.status = 'PENDING' ORDER BY r.created_at ASC, r.id ASC";
        List<ParameterChangeRequestDTO> result = new ArrayList<>();

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                result.add(map(rs));
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        return result;
    }

    /**
     * Looks up a single change request by its primary key.
     *
     * @param id the request identifier to fetch
     * @return the matching request, or {@code null} if no row matches or the query fails
     */
    public ParameterChangeRequestDTO getById(int id) {
        String sql = SELECT_WITH_PARK + "WHERE r.id = ?";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, id);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return map(rs);
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        return null;
    }

    /**
     * Records a decision (approve/reject) on a request, stamping
     * {@code decided_by} and {@code decided_at}.
     *
     * <p><strong>State guard.</strong> The update only fires
     * {@code WHERE id = ? AND status = 'PENDING'}, so a request that has already
     * been decided is left untouched and {@code false} is returned. Folding the
     * guard into the {@code WHERE} clause makes it atomic: two concurrent
     * decisions cannot both win, since only the first update matches a still-PENDING
     * row.
     *
     * @param id        the request to decide
     * @param status    the decision to record ({@code APPROVED} or {@code REJECTED})
     * @param decidedBy the deciding department manager's {@code user.id}
     * @return {@code true} if the row was still PENDING and got updated,
     *         {@code false} if it was already decided, not found, or the query fails
     */
    public boolean decide(int id, ChangeStatus status, int decidedBy) {
        String sql = "UPDATE parameter_change_request " +
                "SET status = ?, decided_by = ?, decided_at = NOW() " +
                "WHERE id = ? AND status = 'PENDING'";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, status.name());
            stmt.setInt(2, decidedBy);
            stmt.setInt(3, id);
            return stmt.executeUpdate() == 1;

        } catch (Exception e) {
            e.printStackTrace();
        }

        return false;
    }

    /**
     * Maps the current {@link ResultSet} row to a {@link ParameterChangeRequestDTO}.
     * {@code created_at}/{@code decided_at} are carried to the client as plain
     * strings since they are display-only.
     *
     * @param rs a result set positioned on a joined request row
     * @return the mapped request
     * @throws SQLException if a column read fails
     */
    private ParameterChangeRequestDTO map(ResultSet rs) throws SQLException {
        int decidedRaw = rs.getInt("decided_by");
        Integer decidedBy = rs.wasNull() ? null : decidedRaw;

        Timestamp created = rs.getTimestamp("created_at");
        Timestamp decided = rs.getTimestamp("decided_at");

        return new ParameterChangeRequestDTO(
                rs.getInt("id"),
                rs.getInt("park_id"),
                rs.getString("park_name"),
                rs.getInt("requested_by"),
                ParamField.valueOf(rs.getString("field")),
                rs.getInt("old_value"),
                rs.getInt("new_value"),
                ChangeStatus.valueOf(rs.getString("status")),
                decidedBy,
                created == null ? null : created.toString(),
                decided == null ? null : decided.toString());
    }
}
