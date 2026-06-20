package server.dao;

import common.dto.VisitDTO;
import common.dto.VisitType;
import server.db.DBConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;

/**
 * Data access object for the {@code visit} table — one row per party that
 * physically enters a park, by reservation or as a casual walk-in.
 *
 * <p>Follows the same conventions as {@link ReservationDAO}: each method opens a
 * short-lived {@link java.sql.Connection} from {@link server.db.DBConnection},
 * runs parameterized statements, and maps rows to {@link VisitDTO}. SQL
 * exceptions are logged and surfaced as a {@code null}/{@code false}/{@code -1}
 * return rather than being propagated, so callers signal failure through the
 * return value.
 *
 * <p>A visit is <em>open</em> while {@code exited_at IS NULL}; closing it stamps
 * the exit time. Live occupancy is therefore the sum of the headcounts of all
 * open visits for a park.
 */
public class VisitDAO {

    /** The visit columns selected by every query, in {@link #map} order. */
    private static final String COLUMNS =
            "id, reservation_id, park_id, visitor_id, entered_at, exited_at, headcount, price_cents, visit_type";

    /**
     * Records a new (open) visit, letting the database stamp {@code entered_at}
     * and leaving {@code exited_at} {@code NULL}.
     *
     * <p>The nullable fields are boxed so a Java {@code null} becomes a SQL
     * {@code NULL}: {@code reservationId} is {@code null} for a casual walk-in,
     * {@code visitorId} for an anonymous party, and {@code visitType}/
     * {@code priceCents} are {@code null} for a reservation visit (they are
     * persisted only for casual visits, which have no reservation to derive from).
     *
     * @param reservationId the backing reservation id, or {@code null} for a casual walk-in
     * @param parkId        the park being entered
     * @param visitorId     the visitor's national id, or {@code null} if anonymous
     * @param headcount     number of people in the party
     * @param visitType     the casual visit's type, or {@code null} for a reservation visit
     * @param priceCents    the casual visit's price in cents, or {@code null} for a reservation visit
     * @return the new auto-generated visit id, or {@code -1} if the insert fails
     */
    public int insertVisit(Integer reservationId, int parkId, Long visitorId, int headcount,
                           VisitType visitType, Integer priceCents) {
        String sql = "INSERT INTO visit " +
                "(reservation_id, park_id, visitor_id, headcount, visit_type, price_cents) " +
                "VALUES (?, ?, ?, ?, ?, ?)";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            if (reservationId != null) {
                stmt.setInt(1, reservationId);
            } else {
                stmt.setNull(1, Types.INTEGER);
            }
            stmt.setInt(2, parkId);
            if (visitorId != null) {
                stmt.setLong(3, visitorId);
            } else {
                stmt.setNull(3, Types.BIGINT);
            }
            stmt.setInt(4, headcount);
            if (visitType != null) {
                stmt.setString(5, visitType.name());
            } else {
                stmt.setNull(5, Types.VARCHAR);
            }
            if (priceCents != null) {
                stmt.setInt(6, priceCents);
            } else {
                stmt.setNull(6, Types.INTEGER);
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
     * Looks up a single visit by its primary key — used to echo the row just
     * created back to the gate (the casual path has neither a confirmation code
     * nor, necessarily, a visitor id to look it up by).
     *
     * @param visitId the visit identifier to fetch
     * @return the matching {@link VisitDTO}, or {@code null} if none matches or the query fails
     */
    public VisitDTO findById(int visitId) {
        String sql = "SELECT " + COLUMNS + " FROM visit WHERE id = ?";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, visitId);
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
     * Finds the open visit (not yet exited) reached by a reservation's
     * confirmation code, joining {@code reservation} to resolve the code. If more
     * than one open visit somehow matches, the most recent wins.
     *
     * @param confirmationCode the booking confirmation code presented at the gate
     * @return the open {@link VisitDTO}, or {@code null} if none is open or the query fails
     */
    public VisitDTO findOpenByConfirmation(int confirmationCode) {
        String sql =
                "SELECT v.id, v.reservation_id, v.park_id, v.visitor_id, v.entered_at, " +
                "       v.exited_at, v.headcount, v.price_cents, v.visit_type " +
                "FROM visit v " +
                "JOIN reservation r ON r.id = v.reservation_id " +
                "WHERE r.confirmation_code = ? AND v.exited_at IS NULL " +
                "ORDER BY v.id DESC LIMIT 1";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, confirmationCode);
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
     * Finds the open visit (not yet exited) for a given visitor. If the visitor
     * has more than one open visit, the most recent wins.
     *
     * @param visitorId the visitor's national id
     * @return the open {@link VisitDTO}, or {@code null} if none is open or the query fails
     */
    public VisitDTO findOpenByVisitor(long visitorId) {
        String sql = "SELECT " + COLUMNS + " FROM visit " +
                "WHERE visitor_id = ? AND exited_at IS NULL " +
                "ORDER BY id DESC LIMIT 1";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setLong(1, visitorId);
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
     * Finds the open visit for a specific reservation. If duplicate open rows ever
     * exist, the most recent one wins.
     *
     * @param reservationId the reservation whose physical visit should be open
     * @return the open visit, or {@code null} when the party is not currently inside
     */
    public VisitDTO findOpenByReservation(int reservationId) {
        String sql = "SELECT " + COLUMNS + " FROM visit " +
                "WHERE reservation_id = ? AND exited_at IS NULL " +
                "ORDER BY id DESC LIMIT 1";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, reservationId);
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
     * Closes an open visit by stamping {@code exited_at = NOW()}. The
     * {@code exited_at IS NULL} guard makes this idempotent — a visit that is
     * already closed is not re-stamped and the method reports {@code false}.
     *
     * @param visitId the visit to close
     * @return {@code true} if an open visit was closed, {@code false} otherwise
     */
    public boolean closeVisit(int visitId) {
        String sql = "UPDATE visit SET exited_at = NOW() WHERE id = ? AND exited_at IS NULL";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, visitId);
            return stmt.executeUpdate() > 0;

        } catch (Exception e) {
            e.printStackTrace();
        }

        return false;
    }

    /**
     * Computes a park's live occupancy: the sum of the headcounts of every open
     * visit (those with {@code exited_at IS NULL}). This is the <em>physical</em>
     * head count inside the park right now, not the booking-time availability.
     *
     * @param parkId the park to measure
     * @return the number of people currently inside (0 if the park is empty), or
     *         {@code -1} if the query fails
     */
    public int currentOccupancy(int parkId) {
        String sql = "SELECT COALESCE(SUM(headcount), 0) AS occupancy " +
                "FROM visit WHERE park_id = ? AND exited_at IS NULL";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, parkId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("occupancy");
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        return -1;
    }

    /**
     * Maps the current {@link ResultSet} row to a {@link VisitDTO}.
     *
     * <p>The {@code DATETIME} columns are read with {@code getString} (matching
     * the DTO's string-timestamp convention and tolerating a {@code NULL}
     * {@code exited_at}). The nullable {@code reservation_id}, {@code visitor_id}
     * and {@code price_cents} columns use {@code wasNull()} so a SQL {@code NULL}
     * becomes a Java {@code null} instead of a zero, and {@code visit_type} maps
     * through {@code valueOf} only when present.
     *
     * @param rs a result set positioned on a visit row (columns in {@link #COLUMNS} order)
     * @return the mapped visit
     * @throws SQLException if a column cannot be read
     */
    private VisitDTO map(ResultSet rs) throws SQLException {
        int reservationRaw = rs.getInt("reservation_id");
        Integer reservationId = rs.wasNull() ? null : reservationRaw;

        long visitorRaw = rs.getLong("visitor_id");
        Long visitorId = rs.wasNull() ? null : visitorRaw;

        int priceRaw = rs.getInt("price_cents");
        Integer priceCents = rs.wasNull() ? null : priceRaw;

        String visitTypeRaw = rs.getString("visit_type");
        VisitType visitType = (visitTypeRaw == null) ? null : VisitType.valueOf(visitTypeRaw);

        return new VisitDTO(
                rs.getInt("id"),
                reservationId,
                rs.getInt("park_id"),
                visitorId,
                rs.getString("entered_at"),
                rs.getString("exited_at"),
                rs.getInt("headcount"),
                priceCents,
                visitType);
    }
}
