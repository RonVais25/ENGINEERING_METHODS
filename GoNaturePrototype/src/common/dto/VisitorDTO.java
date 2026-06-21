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
    /** Serialization-format version identifier. */
    private static final long serialVersionUID = 1L;

    /** National-ID-style visitor identifier (primary key). */
    private long id;
    /** Display name of the visitor. */
    private String fullName;
    /** Contact phone number on file, or {@code null} if none recorded. */
    private String phone;
    /** Contact email on file, or {@code null} if none recorded. */
    private String email;
    /** Whether this visitor holds a subscription. */
    private boolean isSubscriber;

    /**
     * Creates a fully populated visitor.
     *
     * @param id           national-ID-style visitor identifier
     * @param fullName     display name of the visitor
     * @param phone        contact phone number on file, or {@code null}
     * @param email        contact email on file, or {@code null}
     * @param isSubscriber whether the visitor holds a subscription
     */
    public VisitorDTO(long id, String fullName, String phone, String email, boolean isSubscriber) {
        this.id = id;
        this.fullName = fullName;
        this.phone = phone;
        this.email = email;
        this.isSubscriber = isSubscriber;
    }

    /** {@return the national-ID-style visitor identifier} */
    public long getId() {
        return id;
    }

    /** {@return the display name of the visitor} */
    public String getFullName() {
        return fullName;
    }

    /** {@return the contact phone number on file, or {@code null} if none recorded} */
    public String getPhone() {
        return phone;
    }

    /** {@return the contact email on file, or {@code null} if none recorded} */
    public String getEmail() {
        return email;
    }

    /** {@return whether the visitor holds a subscription} */
    public boolean isSubscriber() {
        return isSubscriber;
    }
}
