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
 * <p>Follows the project's standard DAO conventions: each method opens a
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
     * Looks up a single reservation by its booking confirmation code — the lookup
     * the gate performs at entry, where the visitor presents the code printed on
     * their booking. Confirmation codes are not unique by schema; on the (rare)
     * chance two reservations share a code, the most recent row wins.
     *
     * @param confirmationCode the booking confirmation code to match
     * @return the matching {@link ReservationDTO}, or {@code null} if none matches or the query fails
     */
    public ReservationDTO findByConfirmationCode(int confirmationCode) {
        String sql = "SELECT * FROM reservation WHERE confirmation_code = ? ORDER BY id DESC LIMIT 1";

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
     * Updates the lifecycle status of a reservation and stamps
     * {@code status_changed_at = NOW()} in the same statement.
     *
     * <p>This is the single choke point for every status transition (confirm,
     * cancel, accept-grab, exit→completed, no-show), so the timestamp is recorded
     * in one place rather than at each call site. The stamp backs the Cancellations
     * Report's "when was it cancelled" column.
     *
     * @param id     the reservation to update
     * @param status the new status
     * @return {@code true} if a row was updated, {@code false} otherwise
     */
    public boolean updateStatus(int id, ReservationStatus status) {
        String sql = "UPDATE reservation SET status = ?, status_changed_at = NOW() WHERE id = ?";

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
     * Reschedules a reservation: updates the scheduling fields (date, time, party
     * size) <em>and</em> the recomputed price in the same statement.
     *
     * <p>The price is written here because changing the party size changes what the
     * party owes; persisting it alongside the schedule keeps the row's
     * {@code price_cents} consistent with its {@code party_size} instead of leaving
     * a stale price behind. The caller computes the new price (via
     * {@code PricingService}) and passes it in.
     *
     * @param id         the reservation to update
     * @param visitDate  the new visit date, ISO {@code yyyy-MM-dd}
     * @param visitTime  the new visit time ({@code HH:mm:ss}), or {@code null} to clear it
     * @param partySize  the new party size
     * @param priceCents the recomputed price for the new party size, in cents
     * @return {@code true} if a row was updated, {@code false} otherwise
     */
    public boolean updateReschedule(int id, String visitDate, String visitTime, int partySize, int priceCents) {
        String sql = "UPDATE reservation SET visit_date = ?, visit_time = ?, party_size = ?, price_cents = ? WHERE id = ?";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setDate(1, Date.valueOf(visitDate));
            if (visitTime != null) {
                stmt.setTime(2, Time.valueOf(visitTime));
            } else {
                stmt.setNull(2, java.sql.Types.TIME);
            }
            stmt.setInt(3, partySize);
            stmt.setInt(4, priceCents);
            stmt.setInt(5, id);
            return stmt.executeUpdate() > 0;

        } catch (Exception e) {
            e.printStackTrace();
        }

        return false;
    }

    /**
     * Finds the reservations that the no-show sweep should mark
     * {@link ReservationStatus#NO_SHOW}: those still {@code CONFIRMED} whose
     * {@code visit_date} is strictly before today (a same-day booking can still
     * arrive) and for which no {@code visit} row was ever recorded — i.e. the party
     * never entered the park. Ordered by date for a stable sweep.
     *
     * @return the no-show candidates (possibly empty); never {@code null}
     */
    public List<ReservationDTO> findNoShowCandidates() {
        String sql = "SELECT r.* FROM reservation r " +
                "WHERE r.status = 'CONFIRMED' " +
                "  AND r.visit_date < CURDATE() " +
                "  AND NOT EXISTS (SELECT 1 FROM visit v WHERE v.reservation_id = r.id) " +
                "ORDER BY r.visit_date ASC, r.id ASC";
        List<ReservationDTO> result = new ArrayList<>();

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
     * Finds the PENDING reservations due a confirmation reminder: those whose visit
     * (combining {@code visit_date} with {@code visit_time}, or midnight when the
     * time is null) falls between now and {@code leadHours} from now, and that have
     * not already been reminded ({@code reminder_sent_at IS NULL}). The null guard
     * is what stops the reminder being re-sent on every poll. Past visits are
     * excluded — a reminder to confirm a visit that has already happened is moot.
     *
     * @param leadHours how far ahead of the visit a reminder should fire
     * @return the reservations due a reminder (possibly empty); never {@code null}
     */
    public List<ReservationDTO> findReminderCandidates(int leadHours) {
        String sql = "SELECT r.* FROM reservation r " +
                "WHERE r.status = 'PENDING' " +
                "  AND r.reminder_sent_at IS NULL " +
                "  AND TIMESTAMP(r.visit_date, COALESCE(r.visit_time, '00:00:00')) >= NOW() " +
                "  AND TIMESTAMP(r.visit_date, COALESCE(r.visit_time, '00:00:00')) <= NOW() + INTERVAL ? HOUR " +
                "ORDER BY r.visit_date ASC, r.id ASC";
        List<ReservationDTO> result = new ArrayList<>();

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, leadHours);
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
     * Stamps {@code reminder_sent_at = NOW()} on a reservation, marking that its
     * confirmation reminder has gone out so {@link #findReminderCandidates} will
     * skip it on the next poll.
     *
     * @param id the reservation that was just reminded
     * @return {@code true} if a row was updated, {@code false} otherwise
     */
    public boolean markReminderSent(int id) {
        String sql = "UPDATE reservation SET reminder_sent_at = NOW() WHERE id = ?";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, id);
            return stmt.executeUpdate() > 0;

        } catch (Exception e) {
            e.printStackTrace();
        }

        return false;
    }

    /**
     * Finds the PENDING reservations whose confirmation window has lapsed: still
     * {@code PENDING}, already reminded ({@code reminder_sent_at IS NOT NULL}), and
     * reminded more than {@code timeoutMinutes} ago. The {@code status = 'PENDING'}
     * filter is what makes confirming in time safe — once a visitor confirms
     * (PENDING→CONFIRMED) the row drops out of this result and is never cancelled.
     *
     * @param timeoutMinutes the confirmation window length, in minutes
     * @return the reservations to auto-cancel (possibly empty); never {@code null}
     */
    public List<ReservationDTO> findConfirmTimeoutCandidates(int timeoutMinutes) {
        String sql = "SELECT r.* FROM reservation r " +
                "WHERE r.status = 'PENDING' " +
                "  AND r.reminder_sent_at IS NOT NULL " +
                "  AND r.reminder_sent_at + INTERVAL ? MINUTE < NOW() " +
                "ORDER BY r.visit_date ASC, r.id ASC";
        List<ReservationDTO> result = new ArrayList<>();

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, timeoutMinutes);
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
