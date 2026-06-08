package server.dao;

import common.dto.Role;
import common.dto.UserDTO;
import common.dto.VisitorDTO;
import server.db.DBConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLIntegrityConstraintViolationException;

/**
 * Data access object for authentication: staff/visitor credential lookups and
 * the single-login lock backed by the {@code active_session} table.
 *
 * <p>Follows the same conventions as {@link OrderDAO}: each method opens a
 * short-lived {@link java.sql.Connection} from {@link server.db.DBConnection},
 * runs parameterized statements, and maps rows to DTOs. SQL exceptions are
 * logged and surfaced as a {@code null}/{@code false} return rather than being
 * propagated, so callers signal failure through the return value.
 */
public class AuthDAO {

    /**
     * Looks up a staff user by username and password.
     *
     * <p>NOTE: this compares the supplied password against the stored value
     * verbatim — real systems must hash and salt passwords; plaintext is
     * accepted here only because this is a teaching prototype. The returned DTO
     * deliberately omits the password so credentials are never sent back to the
     * client.
     *
     * @param username the login username
     * @param password the plaintext password to match against {@code password_hash}
     * @return the matching {@link UserDTO}, or {@code null} if no row matches or the query fails
     */
    public UserDTO findStaffByCredentials(String username, String password) {
        String sql = "SELECT id, username, full_name, role, park_id " +
                     "FROM `user` WHERE username = ? AND password_hash = ?";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, username);
            stmt.setString(2, password);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    int parkRaw = rs.getInt("park_id");
                    Integer parkId = rs.wasNull() ? null : parkRaw;
                    return new UserDTO(
                            rs.getInt("id"),
                            rs.getString("username"),
                            rs.getString("full_name"),
                            Role.valueOf(rs.getString("role")),
                            parkId
                    );
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        return null;
    }

    /**
     * Looks up a staff user by their primary key.
     *
     * <p>Used for server-side authorization: the {@link server.net.ClientSession}
     * carries only the logged-in actor's id (not their role), so a controller
     * that must gate an operation by role re-reads the {@code user} row here. As
     * with {@link #findStaffByCredentials}, the returned DTO omits the password.
     *
     * @param id the user identifier to fetch
     * @return the matching {@link UserDTO}, or {@code null} if no row matches or the query fails
     */
    public UserDTO findUserById(long id) {
        String sql = "SELECT id, username, full_name, role, park_id FROM `user` WHERE id = ?";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setLong(1, id);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    int parkRaw = rs.getInt("park_id");
                    Integer parkId = rs.wasNull() ? null : parkRaw;
                    return new UserDTO(
                            rs.getInt("id"),
                            rs.getString("username"),
                            rs.getString("full_name"),
                            Role.valueOf(rs.getString("role")),
                            parkId
                    );
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        return null;
    }

    /**
     * Looks up a visitor by their national ID (login-by-ID).
     *
     * @param id the visitor identifier to fetch
     * @return the matching {@link VisitorDTO}, or {@code null} if no row matches or the query fails
     */
    public VisitorDTO findVisitorById(long id) {
        String sql = "SELECT id, full_name, is_subscriber FROM visitor WHERE id = ?";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setLong(1, id);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return new VisitorDTO(
                            rs.getLong("id"),
                            rs.getString("full_name"),
                            rs.getBoolean("is_subscriber")
                    );
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        return null;
    }

    /**
     * Acquires the single-login lock for an actor by inserting a row into
     * {@code active_session}.
     *
     * <p>Uses an insert-and-catch strategy rather than check-then-insert: the
     * {@code (actor_id, kind)} primary key makes a concurrent second login race
     * fail at the database with a duplicate-key violation, which is caught and
     * reported as {@code false}. A check-then-insert could let two logins both
     * pass the check before either inserts.
     *
     * @param actorId the user or visitor id to lock
     * @param kind    the actor kind, {@code "USER"} or {@code "VISITOR"}
     * @return {@code true} if the lock was acquired, {@code false} if the actor is
     *         already locked elsewhere or the insert fails
     */
    public boolean lock(long actorId, String kind) {
        String sql = "INSERT INTO active_session (actor_id, kind) VALUES (?, ?)";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setLong(1, actorId);
            stmt.setString(2, kind);
            stmt.executeUpdate();
            return true;

        } catch (SQLIntegrityConstraintViolationException dup) {
            // Duplicate (actor_id, kind) -> already logged in elsewhere.
            return false;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Releases the single-login lock for an actor by deleting its
     * {@code active_session} row. No-op if no row exists.
     *
     * @param actorId the user or visitor id to unlock
     * @param kind    the actor kind, {@code "USER"} or {@code "VISITOR"}
     */
    public void unlock(long actorId, String kind) {
        String sql = "DELETE FROM active_session WHERE actor_id = ? AND kind = ?";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setLong(1, actorId);
            stmt.setString(2, kind);
            stmt.executeUpdate();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
