package common.dto;

import java.io.Serializable;

/**
 * Immutable data transfer object representing a visitor who logged in by
 * national ID.
 *
 * <p>Created server-side by {@link server.dao.AuthDAO} from a {@code visitor} row and
 * serialized across the socket to the client inside a {@link ServerResponse} on a
 * successful visitor login.
 */
public class VisitorDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    /** National-ID-style visitor identifier (primary key). */
    private long id;
    /** Display name of the visitor. */
    private String fullName;
    /** Whether this visitor holds a subscription. */
    private boolean isSubscriber;

    /**
     * Creates a fully populated visitor.
     *
     * @param id           national-ID-style visitor identifier
     * @param fullName     display name of the visitor
     * @param isSubscriber whether the visitor holds a subscription
     */
    public VisitorDTO(long id, String fullName, boolean isSubscriber) {
        this.id = id;
        this.fullName = fullName;
        this.isSubscriber = isSubscriber;
    }

    /** @return the national-ID-style visitor identifier */
    public long getId() {
        return id;
    }

    /** @return the display name of the visitor */
    public String getFullName() {
        return fullName;
    }

    /** @return whether the visitor holds a subscription */
    public boolean isSubscriber() {
        return isSubscriber;
    }
}
