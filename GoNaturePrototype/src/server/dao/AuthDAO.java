package server.dao;

import common.dto.Role;
import common.dto.UserDTO;
import common.dto.VisitorDTO;
import server.db.DBConnection;
import server.util.ServerLog;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLIntegrityConstraintViolationException;

/**
 * Data access object for authentication: staff/visitor credential lookups and
 * the single-login lock backed by the {@code active_session} table.
 *
 * <p>Follows the project's standard DAO conventions: each method opens a
 * short-lived {@link java.sql.Connection} from {@link server.db.DBConnection},
 * runs parameterized statements, and maps rows to DTOs. SQL exceptions are
 * logged and surfaced as a {@code null}/{@code false} return rather than being
 * propagated, so callers signal failure through the return value.
 */
public class AuthDAO {

    /** Creates the authentication DAO. */
    public AuthDAO() { }

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
            ServerLog.daoError(e);
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
            ServerLog.daoError(e);
        }

        return null;
    }

    /**
     * Authenticates a visitor by national ID and password (visitor login).
     *
     * <p>The visitor mirror of {@link #findStaffByCredentials}: the supplied
     * password is matched against {@code visitor.password_hash} verbatim (plaintext
     * is accepted only because this is a teaching prototype — see that method's
     * note). A {@code null} return covers both an unknown id and a wrong password,
     * so the controller can reject them with a single non-revealing message. The
     * returned DTO omits the password so credentials are never sent to the client.
     *
     * @param id       the visitor's national id
     * @param password the plaintext password to match against {@code password_hash}
     * @return the matching {@link VisitorDTO}, or {@code null} if no row matches or the query fails
     */
    public VisitorDTO authenticateVisitor(long id, String password) {
        String sql = "SELECT id, full_name, phone, email, is_subscriber " +
                     "FROM visitor WHERE id = ? AND password_hash = ?";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setLong(1, id);
            stmt.setString(2, password);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return new VisitorDTO(
                            rs.getLong("id"),
                            rs.getString("full_name"),
                            rs.getString("phone"),
                            rs.getString("email"),
                            rs.getBoolean("is_subscriber")
                    );
                }
            }

        } catch (Exception e) {
            ServerLog.daoError(e);
        }

        return null;
    }

    /**
     * Self-service signup: inserts a brand-new registered visitor with their chosen
     * password, as a regular (non-subscriber) account.
     *
     * <p>Distinct from the SERVICE_REP subscriber/guide paths in {@link MemberDAO}:
     * this never raises {@code is_subscriber} (a self-registered user is a plain
     * visitor) and stores the password the user picked rather than the seeded
     * default. The insert is attempted directly and a duplicate primary key —
     * meaning the national id is already registered — is caught and reported as
     * {@code false}, mirroring {@link #lock}'s insert-and-catch so no
     * check-then-insert race is opened.
     *
     * @param id       the visitor's national id (primary key)
     * @param fullName the visitor's display name
     * @param email    the visitor's email address
     * @param phone    the visitor's phone number
     * @param password the plaintext password to store in {@code password_hash}
     * @return {@code true} if a new visitor was created, {@code false} if the id is
     *         already registered or the insert fails
     */
    public boolean registerVisitor(long id, String fullName, String email, String phone, String password) {
        String sql = "INSERT INTO visitor (id, full_name, phone, email, is_subscriber, password_hash) " +
                     "VALUES (?, ?, ?, ?, FALSE, ?)";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setLong(1, id);
            stmt.setString(2, fullName);
            stmt.setString(3, phone);
            stmt.setString(4, email);
            stmt.setString(5, password);
            stmt.executeUpdate();
            return true;

        } catch (SQLIntegrityConstraintViolationException dup) {
            // Duplicate visitor.id -> the national id is already registered.
            return false;
        } catch (Exception e) {
            ServerLog.daoError(e);
            return false;
        }
    }

    /**
     * Looks up a visitor by their national ID, without a password check.
     *
     * <p>Retained for the booking/pricing/contact lookups that need a visitor's
     * details (e.g. the member-discount check and notification targets) but are not
     * a login — visitor sign-in itself goes through {@link #authenticateVisitor}.
     *
     * @param id the visitor identifier to fetch
     * @return the matching {@link VisitorDTO}, or {@code null} if no row matches or the query fails
     */
    public VisitorDTO findVisitorById(long id) {
        String sql = "SELECT id, full_name, phone, email, is_subscriber FROM visitor WHERE id = ?";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setLong(1, id);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return new VisitorDTO(
                            rs.getLong("id"),
                            rs.getString("full_name"),
                            rs.getString("phone"),
                            rs.getString("email"),
                            rs.getBoolean("is_subscriber")
                    );
                }
            }

        } catch (Exception e) {
            ServerLog.daoError(e);
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
            ServerLog.daoError(e);
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
            ServerLog.daoError(e);
        }
    }
}
