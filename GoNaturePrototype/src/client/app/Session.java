package client.app;

import client.net.ClientConnection;
import common.dto.Role;
import common.dto.UserDTO;
import common.dto.VisitorDTO;

/**
 * Holds connection details and the logged-in identity for the lifetime of the
 * client process. One instance is created in {@code GoNatureClientApp} and passed
 * around via the FXMLLoader controller factory.
 *
 * <p>Exactly one of {@link #user} (staff) or {@link #visitor} is non-null while
 * logged in; both are null when logged out. The convenience getters
 * ({@link #isStaff()}, {@link #getRole()}, {@link #getActorId()}, …) read off
 * whichever is set, so callers can stay agnostic about which kind logged in.
 */
public class Session {

    // Connection (set by the connection screen).
    private ClientConnection connection;
    private String host;
    private int port;

    // Identity (set by the user-login screen). At most one is non-null.
    private UserDTO    user;     // staff login
    private VisitorDTO visitor;  // visitor login-by-ID

    public ClientConnection getConnection() { return connection; }
    public String getHost()                 { return host; }
    public int getPort()                    { return port; }

    /** Promotes a freshly probed connection into the session (connection screen). */
    public void login(ClientConnection conn, String host, int port) {
        this.connection = conn;
        this.host = host;
        this.port = port;
    }

    /* ---------- Identity ---------------------------------------------------- */

    /** Records a successful staff login (clears any visitor identity). */
    public void setUser(UserDTO user) {
        this.user = user;
        this.visitor = null;
    }

    /** Records a successful visitor login (clears any staff identity). */
    public void setVisitor(VisitorDTO visitor) {
        this.visitor = visitor;
        this.user = null;
    }

    /** Drops the logged-in identity (on logout) so the next user starts clean. */
    public void clearIdentity() {
        this.user = null;
        this.visitor = null;
    }

    /** @return the logged-in staff user, or {@code null} if none/visitor */
    public UserDTO getUser() { return user; }

    /** @return the logged-in visitor, or {@code null} if none/staff */
    public VisitorDTO getVisitor() { return visitor; }

    /** @return {@code true} if a staff user is logged in */
    public boolean isStaff() { return user != null; }

    /** @return {@code true} if a visitor is logged in */
    public boolean isVisitor() { return visitor != null; }

    /** @return {@code true} if any identity (staff or visitor) is logged in */
    public boolean isLoggedIn() { return user != null || visitor != null; }

    /** @return the staff role, or {@code null} for a visitor / when logged out */
    public Role getRole() { return user != null ? user.getRole() : null; }

    /**
     * @return the logged-in actor's id (the user id for staff, the national id
     *         for a visitor), or {@code -1} if logged out
     */
    public long getActorId() {
        if (user != null)    return user.getId();
        if (visitor != null) return visitor.getId();
        return -1L;
    }

    /**
     * @return the park id the staff user belongs to, or {@code null} for
     *         non-park staff, visitors, or when logged out
     */
    public Integer getParkId() {
        return user != null ? user.getParkId() : null;
    }

    /** @return the display name of whoever is logged in, or {@code ""} if no one */
    public String getDisplayName() {
        if (user != null)    return user.getFullName();
        if (visitor != null) return visitor.getFullName();
        return "";
    }

    /**
     * @return a short chrome label for the logged-in identity: the staff role
     *         name for staff, {@code "Subscriber"}/{@code "Visitor"} for visitors,
     *         or {@code ""} when logged out
     */
    public String getRoleLabel() {
        if (user != null)    return user.getRole().name();
        if (visitor != null) return visitor.isSubscriber() ? "Subscriber" : "Visitor";
        return "";
    }

    public void closeConnection() {
        if (connection != null) connection.close();
    }
}
