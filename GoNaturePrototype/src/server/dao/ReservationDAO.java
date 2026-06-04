package server.dao;

import common.dto.ReservationDTO;
import common.dto.ReservationStatus;
import common.dto.VisitType;
import server.db.DBConnection;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.Time;
import java.util.ArrayList;
import java.util.List;

/**
 * Data access object for the {@code reservation} table.
 *
 * <p>Follows the same conventions as {@link OrderDAO}: each method opens a
 * short-lived {@link java.sql.Connection} from {@link server.db.DBConnection},
 * runs parameterized statements, and maps rows to {@link ReservationDTO}. SQL
 * exceptions are logged and surfaced as a {@code null}/{@code false}/{@code -1}
 * return rather than being propagated, so callers signal failure through the
 * return value.
 */
public class ReservationDAO {

    /**
     * Looks up a single reservation by its primary key.
     *
     * @param id the reservation identifier to fetch
     * @return the matching {@link ReservationDTO}, or {@code null} if no row matches or the query fails
     */
    public ReservationDTO getById(int id) {
        String sql = "SELECT * FROM reservation WHERE id = ?";

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
     * Lists every reservation owned by a visitor, most recent visit first.
     *
     * @param visitorId the visitor whose reservations to fetch
     * @return the visitor's reservations (possibly empty); never {@code null}
     */
    public List<ReservationDTO> findByVisitor(long visitorId) {
        String sql = "SELECT * FROM reservation WHERE visitor_id = ? ORDER BY visit_date DESC, id DESC";
        List<ReservationDTO> result = new ArrayList<>();

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setLong(1, visitorId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    result.add(map(rs));
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        return result;
    }

    /**
     * Inserts a new reservation, letting the database assign the auto-increment id.
     *
     * @param r the reservation to persist (its {@code id} and {@code createdAt} are ignored)
     * @return the new auto-generated id, or {@code -1} if the insert fails
     */
    public int insert(ReservationDTO r) {
        String sql = "INSERT INTO reservation " +
                "(park_id, visitor_id, visit_date, visit_time, party_size, visit_type, status, " +
                "is_group, guide_id, price_cents, paid_in_advance, confirmation_code) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            stmt.setInt(1, r.getParkId());
            stmt.setLong(2, r.getVisitorId());
            stmt.setDate(3, Date.valueOf(r.getVisitDate()));
            if (r.getVisitTime() != null) {
                stmt.setTime(4, Time.valueOf(r.getVisitTime()));
            } else {
                stmt.setNull(4, java.sql.Types.TIME);
            }
            stmt.setInt(5, r.getPartySize());
            stmt.setString(6, r.getVisitType().name());
            stmt.setString(7, r.getStatus().name());
            stmt.setBoolean(8, r.isGroup());
            if (r.getGuideId() != null) {
                stmt.setLong(9, r.getGuideId());
            } else {
                stmt.setNull(9, java.sql.Types.BIGINT);
            }
            stmt.setInt(10, r.getPriceCents());
            stmt.setBoolean(11, r.isPaidInAdvance());
            if (r.getConfirmationCode() != null) {
                stmt.setInt(12, r.getConfirmationCode());
            } else {
                stmt.setNull(12, java.sql.Types.INTEGER);
            }

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
     * Updates the lifecycle status of a reservation.
     *
     * @param id     the reservation to update
     * @param status the new status
     * @return {@code true} if a row was updated, {@code false} otherwise
     */
    public boolean updateStatus(int id, ReservationStatus status) {
        String sql = "UPDATE reservation SET status = ? WHERE id = ?";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, status.name());
            stmt.setInt(2, id);
            return stmt.executeUpdate() > 0;

        } catch (Exception e) {
            e.printStackTrace();
        }

        return false;
    }

    /**
     * Updates the scheduling fields (date, time, party size) of a reservation.
     *
     * @param id        the reservation to update
     * @param visitDate the new visit date, ISO {@code yyyy-MM-dd}
     * @param visitTime the new visit time ({@code HH:mm:ss}), or {@code null} to clear it
     * @param partySize the new party size
     * @return {@code true} if a row was updated, {@code false} otherwise
     */
    public boolean updateDateAndParty(int id, String visitDate, String visitTime, int partySize) {
        String sql = "UPDATE reservation SET visit_date = ?, visit_time = ?, party_size = ? WHERE id = ?";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setDate(1, Date.valueOf(visitDate));
            if (visitTime != null) {
                stmt.setTime(2, Time.valueOf(visitTime));
            } else {
                stmt.setNull(2, java.sql.Types.TIME);
            }
            stmt.setInt(3, partySize);
            stmt.setInt(4, id);
            return stmt.executeUpdate() > 0;

        } catch (Exception e) {
            e.printStackTrace();
        }

        return false;
    }

    /**
     * Computes the remaining headroom for a park on a given date: the park's
     * {@code max_capacity} minus its {@code gap_size} minus the total party size
     * of all {@code PENDING}/{@code CONFIRMED} reservations for that park and date.
     *
     * @param parkId    the park to check
     * @param visitDate the visit date to check, ISO {@code yyyy-MM-dd}
     * @return the remaining capacity (may be negative if overbooked), or {@code -1} if the
     *         park is unknown or the query fails
     */
    public int availableCapacity(int parkId, String visitDate) {
        String sql =
                "SELECT p.max_capacity - p.gap_size - COALESCE(SUM(r.party_size), 0) AS headroom " +
                "FROM park p " +
                "LEFT JOIN reservation r " +
                "  ON r.park_id = p.id " +
                "  AND r.visit_date = ? " +
                "  AND r.status IN ('PENDING', 'CONFIRMED') " +
                "WHERE p.id = ? " +
                "GROUP BY p.id, p.max_capacity, p.gap_size";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setDate(1, Date.valueOf(visitDate));
            stmt.setInt(2, parkId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("headroom");
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        return -1;
    }

    /**
     * Checks whether a registered guide exists with the given visitor id.
     *
     * @param visitorId the candidate guide's visitor (national) id
     * @return {@code true} if a matching row exists in the {@code guide} table,
     *         {@code false} if none matches or the query fails
     */
    public boolean guideExists(long visitorId) {
        String sql = "SELECT 1 FROM guide WHERE visitor_id = ?";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setLong(1, visitorId);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next();
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        return false;
    }

    /**
     * Maps the current row of a {@link ResultSet} to a {@link ReservationDTO}.
     *
     * <p>Date/time columns are read with {@code getString} (matching the DTO's
     * String-date convention and tolerating a {@code NULL} {@code visit_time}).
     * The enum columns are parsed with {@code valueOf}, and the nullable
     * {@code guide_id}/{@code confirmation_code} columns use {@code wasNull()} so a
     * SQL {@code NULL} becomes a Java {@code null} instead of a zero.
     *
     * @param rs a result set positioned on a reservation row
     * @return the mapped reservation
     * @throws java.sql.SQLException if a column cannot be read
     */
    private ReservationDTO map(ResultSet rs) throws java.sql.SQLException {
        long guideRaw = rs.getLong("guide_id");
        Long guideId = rs.wasNull() ? null : guideRaw;

        int codeRaw = rs.getInt("confirmation_code");
        Integer confirmationCode = rs.wasNull() ? null : codeRaw;

        return new ReservationDTO(
                rs.getInt("id"),
                rs.getInt("park_id"),
                rs.getLong("visitor_id"),
                rs.getString("visit_date"),
                rs.getString("visit_time"),
                rs.getInt("party_size"),
                VisitType.valueOf(rs.getString("visit_type")),
                ReservationStatus.valueOf(rs.getString("status")),
                rs.getBoolean("is_group"),
                guideId,
                rs.getInt("price_cents"),
                rs.getBoolean("paid_in_advance"),
                confirmationCode,
                rs.getString("created_at")
        );
    }
}
