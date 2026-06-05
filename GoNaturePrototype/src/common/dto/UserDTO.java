package common.dto;

import java.io.Serializable;

/**
 * Immutable data transfer object representing an authenticated staff user.
 *
 * <p>Created server-side by {@link server.dao.AuthDAO} from a {@code user} row and
 * serialized across the socket to the client inside a {@link ServerResponse} on a
 * successful staff login. By design it carries <strong>no</strong> password / hash
 * field — credentials are never sent back to the client.
 */
public class UserDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    /** Unique user identifier (primary key). */
    private int id;
    /** Login username. */
    private String username;
    /** Display name of the user. */
    private String fullName;
    /** Staff role governing the user's permissions. */
    private Role role;
    /** Identifier of the park the user belongs to, or {@code null} if none. */
    private Integer parkId;

    /**
     * Creates a fully populated staff user.
     *
     * @param id       unique user identifier
     * @param username login username
     * @param fullName display name of the user
     * @param role     staff role governing permissions
     * @param parkId   identifier of the user's park, or {@code null} if none
     */
    public UserDTO(int id, String username, String fullName, Role role, Integer parkId) {
        this.id = id;
        this.username = username;
        this.fullName = fullName;
        this.role = role;
        this.parkId = parkId;
    }

    /** @return the unique user identifier */
    public int getId() {
        return id;
    }

    /** @return the login username */
    public String getUsername() {
        return username;
    }

    /** @return the display name of the user */
    public String getFullName() {
        return fullName;
    }

    /** @return the staff role governing the user's permissions */
    public Role getRole() {
        return role;
    }

    /** @return the identifier of the user's park, or {@code null} if none */
    public Integer getParkId() {
        return parkId;
    }
}
