package server.dao;

import common.dto.ParamField;
import common.dto.ParkDTO;
import server.db.DBConnection;
import server.util.ServerLog;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Data access object for the {@code park} table.
 *
 * <p>Follows the same conventions as {@link ReservationDAO}: each method opens a
 * short-lived {@link java.sql.Connection} from {@link server.db.DBConnection},
 * runs parameterized statements, and maps rows to {@link ParkDTO}. SQL exceptions
 * are logged and surfaced as a {@code null}/empty-list return rather than being
 * propagated, so callers signal failure through the return value.
 */
public class ParkDAO {

    /** Creates the park DAO. */
    public ParkDAO() { }

    /** The columns selected by every park query, in {@link #map} order. */
    private static final String COLUMNS =
            "id, name, max_capacity, gap_size, default_stay_minutes, manager_id";

    /**
     * Looks up a single park by its primary key.
     *
     * @param id the park identifier to fetch
     * @return the matching {@link ParkDTO}, or {@code null} if no row matches or the query fails
     */
    public ParkDTO getById(int id) {
        String sql = "SELECT " + COLUMNS + " FROM park WHERE id = ?";

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
     * Looks up the park a given staff user manages (via {@code park.manager_id}).
     *
     * @param userId the manager's {@code user.id}
     * @return the park they manage, or {@code null} if they manage none or the query fails
     */
    public ParkDTO getByManager(int userId) {
        String sql = "SELECT " + COLUMNS + " FROM park WHERE manager_id = ?";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, userId);
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
     * Lists every park, alphabetically by name — used to populate the booking
     * park dropdown.
     *
     * @return all parks (possibly empty); never {@code null}
     */
    public List<ParkDTO> listAll() {
        String sql = "SELECT " + COLUMNS + " FROM park ORDER BY name";
        List<ParkDTO> result = new ArrayList<>();

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
     * Writes a new value to a single park parameter column. Called only after a
     * department manager approves a change request — never directly from a client
     * request.
     *
     * @param parkId   the park to update
     * @param field    which parameter column to write
     * @param newValue the value to store
     */
    public void updateField(int parkId, ParamField field, int newValue) {
        // The column name is derived from a fixed switch over the ParamField enum
        // (never from client text), so concatenating it into the SQL is safe.
        String sql = "UPDATE park SET " + columnFor(field) + " = ? WHERE id = ?";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, newValue);
            stmt.setInt(2, parkId);
            stmt.executeUpdate();

        } catch (Exception e) {
            ServerLog.daoError(e);
        }
    }

    /**
     * Maps a {@link ParamField} to its {@code park} column name.
     *
     * @param field the parameter field
     * @return the matching column name
     */
    private static String columnFor(ParamField field) {
        switch (field) {
            case MAX_CAPACITY:         return "max_capacity";
            case GAP_SIZE:             return "gap_size";
            case DEFAULT_STAY_MINUTES: return "default_stay_minutes";
            default:
                throw new IllegalArgumentException("Unknown park parameter field: " + field);
        }
    }

    /**
     * Maps the current {@link ResultSet} row to a {@link ParkDTO}.
     *
     * @param rs a result set positioned on a park row (columns in {@link #COLUMNS} order)
     * @return the mapped park
     * @throws SQLException if a column read fails
     */
    private ParkDTO map(ResultSet rs) throws SQLException {
        int mgrRaw = rs.getInt("manager_id");
        Integer managerId = rs.wasNull() ? null : mgrRaw;
        return new ParkDTO(
                rs.getInt("id"),
                rs.getString("name"),
                rs.getInt("max_capacity"),
                rs.getInt("gap_size"),
                rs.getInt("default_stay_minutes"),
                managerId);
    }
}
