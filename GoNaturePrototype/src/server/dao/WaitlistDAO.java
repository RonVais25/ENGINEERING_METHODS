package server.dao;

import common.dto.WaitlistEntryDTO;
import server.db.DBConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * Data access object for the {@code waiting_list_entry} table.
 *
 * <p>Follows the same conventions as {@link ReservationDAO}: each method opens a
 * short-lived {@link java.sql.Connection} from {@link server.db.DBConnection},
 * runs parameterized statements, and maps rows to {@link WaitlistEntryDTO}. SQL
 * exceptions are logged and surfaced as a {@code null}/{@code false}/{@code -1}
 * return rather than being propagated, so callers signal failure through the
 * return value.
 *
 * <p>Every read builds on {@link #SELECT_BASE}, which {@code JOIN}s the parent
 * {@code reservation} so {@link #map} can populate the entry's convenience fields
 * (park, date, party size, visitor). An entry's queue position is its
 * {@code queued_at} stamp, so the FIFO reads order by it ascending. An entry is
 * considered to hold an <em>active</em> offer once {@code grab_offered_at} is set;
 * it leaves that state only by being removed (accept / decline / expiry), so a
 * non-null {@code grab_offered_at} is treated as "already being offered to" and
 * excluded from {@link #findNextEligible}.
 */
public class WaitlistDAO {

    /**
     * Shared SELECT prefix for every read. Joins the parent reservation so
     * {@link #map} can resolve park/date/party/visitor in one round-trip. Callers
     * append a {@code WHERE}/{@code ORDER BY} clause.
     */
    private static final String SELECT_BASE =
            "SELECT w.id, w.reservation_id, w.queued_at, w.grab_offered_at, w.grab_expires_at, " +
            "       r.park_id, r.visit_date, r.party_size, r.visitor_id " +
            "FROM waiting_list_entry w " +
            "JOIN reservation r ON r.id = w.reservation_id ";

    /**
     * Inserts a new queue entry for a WAITING reservation, letting the database
     * stamp {@code queued_at = NOW()} and leaving the offer columns
     * ({@code grab_offered_at}/{@code grab_expires_at}) NULL until a slot is
     * offered via {@link #markOffered}.
     *
     * @param reservationId the WAITING reservation to queue
     * @return the new auto-generated entry id, or {@code -1} if the insert fails
     */
    public int insertEntry(int reservationId) {
        String sql = "INSERT INTO waiting_list_entry (reservation_id) VALUES (?)";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            stmt.setInt(1, reservationId);
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
     * Looks up the queue entry for a given reservation.
     *
     * @param reservationId the reservation whose entry to fetch
     * @return the matching {@link WaitlistEntryDTO}, or {@code null} if none matches or the query fails
     */
    public WaitlistEntryDTO getByReservation(int reservationId) {
        String sql = SELECT_BASE + "WHERE w.reservation_id = ?";

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
     * Looks up a single queue entry by its primary key.
     *
     * @param id the waiting-list entry identifier to fetch
     * @return the matching {@link WaitlistEntryDTO}, or {@code null} if none matches or the query fails
     */
    public WaitlistEntryDTO getById(int id) {
        String sql = SELECT_BASE + "WHERE w.id = ?";

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
     * Finds the FIFO-first queue entry eligible to be offered a freed slot: the
     * earliest-queued entry whose reservation is still {@code WAITING} for the
     * given park and date, that does not already hold an offer, and whose party
     * fits the freed capacity ("must-fit"). A party larger than {@code freedCapacity}
     * is skipped so the queue is not blocked behind a too-large group.
     *
     * @param parkId        the park a slot freed up for
     * @param visitDate     the visit date a slot freed up for, ISO {@code yyyy-MM-dd}
     * @param freedCapacity the number of freed places the next entry's party must fit within
     * @return the next eligible {@link WaitlistEntryDTO}, or {@code null} if none qualifies or the query fails
     */
    public WaitlistEntryDTO findNextEligible(int parkId, String visitDate, int freedCapacity) {
        String sql = SELECT_BASE +
                "WHERE r.status = 'WAITING' " +
                "  AND r.park_id = ? " +
                "  AND r.visit_date = ? " +
                "  AND w.grab_offered_at IS NULL " +
                "  AND r.party_size <= ? " +
                "ORDER BY w.queued_at ASC, w.id ASC " +
                "LIMIT 1";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, parkId);
            stmt.setString(2, visitDate);
            stmt.setInt(3, freedCapacity);
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
     * Marks an entry as offered the freed slot: stamps {@code grab_offered_at = NOW()}
     * and sets {@code grab_expires_at} to the supplied deadline (the caller computes
     * it, typically one hour out). After this the entry holds an active offer and is
     * excluded from {@link #findNextEligible}.
     *
     * @param entryId       the waiting-list entry to mark
     * @param grabExpiresAt the offer's expiry, as a {@code yyyy-MM-dd HH:mm:ss} string
     */
    public void markOffered(int entryId, String grabExpiresAt) {
        String sql = "UPDATE waiting_list_entry SET grab_offered_at = NOW(), grab_expires_at = ? WHERE id = ?";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, grabExpiresAt);
            stmt.setInt(2, entryId);
            stmt.executeUpdate();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Removes a queue entry — when its visitor accepts the grab, leaves the list,
     * or lets an offer expire. The parent reservation's lifecycle is handled
     * separately by the caller.
     *
     * @param entryId the waiting-list entry to delete
     */
    public void removeEntry(int entryId) {
        String sql = "DELETE FROM waiting_list_entry WHERE id = ?";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, entryId);
            stmt.executeUpdate();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Lists every entry whose active offer has lapsed — those with an offer set
     * ({@code grab_offered_at IS NOT NULL}) whose {@code grab_expires_at} is now in
     * the past, ordered FIFO. These are the entries that forfeited their slot and
     * must be removed so the grab can advance; see
     * {@link server.control.ReservationController#expireOverdueOffers()}.
     *
     * @return the lapsed-offer entries (possibly empty); never {@code null}
     */
    public List<WaitlistEntryDTO> findExpiredOffers() {
        String sql = SELECT_BASE +
                "WHERE w.grab_offered_at IS NOT NULL " +
                "  AND w.grab_expires_at < NOW() " +
                "ORDER BY w.queued_at ASC, w.id ASC";
        List<WaitlistEntryDTO> result = new ArrayList<>();

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
     * Maps the current row of a {@link ResultSet} (selected via {@link #SELECT_BASE})
     * to a {@link WaitlistEntryDTO}. The {@code DATETIME} columns are read with
     * {@code getString} (matching the DTO's string convention and tolerating the
     * nullable offer columns).
     *
     * @param rs a result set positioned on a joined waiting-list row
     * @return the mapped entry
     * @throws java.sql.SQLException if a column cannot be read
     */
    private WaitlistEntryDTO map(ResultSet rs) throws java.sql.SQLException {
        return new WaitlistEntryDTO(
                rs.getInt("id"),
                rs.getInt("reservation_id"),
                rs.getString("queued_at"),
                rs.getString("grab_offered_at"),
                rs.getString("grab_expires_at"),
                rs.getInt("park_id"),
                rs.getString("visit_date"),
                rs.getInt("party_size"),
                rs.getLong("visitor_id"));
    }
}
