package common.dto;

import java.io.Serializable;

/**
 * Immutable data transfer object representing a {@code parameter_change_request}
 * row — the unit of the park-parameter approval workflow.
 *
 * <p>A {@code PARK_MANAGER} creates one (status {@link ChangeStatus#PENDING}); a
 * {@code DEPT_MANAGER} approves or rejects it. {@code parkName} is <strong>not</strong>
 * a column — {@link server.dao.ParameterChangeDAO} joins it in from {@code park}
 * purely for display, so the approval screen can show the park's name without a
 * second lookup.
 */
public class ParameterChangeRequestDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    /** Unique request identifier (primary key). */
    private int id;
    /** Identifier of the park the change targets. */
    private int parkId;
    /** Display name of the target park, joined in for convenience. */
    private String parkName;
    /** Identifier of the park manager who requested the change ({@code user.id}). */
    private int requestedBy;
    /** Which park parameter the change targets. */
    private ParamField field;
    /** The park's value for that field when the request was made. */
    private int oldValue;
    /** The requested new value. */
    private int newValue;
    /** Current lifecycle status of the request. */
    private ChangeStatus status;
    /** Department manager who decided it ({@code user.id}), or {@code null} while PENDING. */
    private Integer decidedBy;
    /** When the request was created (string form), or {@code null}. */
    private String createdAt;
    /** When the request was decided (string form), or {@code null} while PENDING. */
    private String decidedAt;

    /**
     * Creates a fully populated change request.
     *
     * @param id          unique request identifier
     * @param parkId      identifier of the target park
     * @param parkName    display name of the target park (joined in for convenience)
     * @param requestedBy id of the park manager who requested the change
     * @param field       which park parameter the change targets
     * @param oldValue    the park's value for that field when the request was made
     * @param newValue    the requested new value
     * @param status      current lifecycle status
     * @param decidedBy   id of the deciding department manager, or {@code null} while PENDING
     * @param createdAt   creation timestamp (string form), or {@code null}
     * @param decidedAt   decision timestamp (string form), or {@code null} while PENDING
     */
    public ParameterChangeRequestDTO(int id, int parkId, String parkName, int requestedBy,
                                     ParamField field, int oldValue, int newValue,
                                     ChangeStatus status, Integer decidedBy,
                                     String createdAt, String decidedAt) {
        this.id = id;
        this.parkId = parkId;
        this.parkName = parkName;
        this.requestedBy = requestedBy;
        this.field = field;
        this.oldValue = oldValue;
        this.newValue = newValue;
        this.status = status;
        this.decidedBy = decidedBy;
        this.createdAt = createdAt;
        this.decidedAt = decidedAt;
    }

    /** @return the unique request identifier */
    public int getId() {
        return id;
    }

    /** @return the identifier of the target park */
    public int getParkId() {
        return parkId;
    }

    /** @return the display name of the target park */
    public String getParkName() {
        return parkName;
    }

    /** @return the id of the park manager who requested the change */
    public int getRequestedBy() {
        return requestedBy;
    }

    /** @return which park parameter the change targets */
    public ParamField getField() {
        return field;
    }

    /** @return the park's value for that field when the request was made */
    public int getOldValue() {
        return oldValue;
    }

    /** @return the requested new value */
    public int getNewValue() {
        return newValue;
    }

    /** @return the current lifecycle status of the request */
    public ChangeStatus getStatus() {
        return status;
    }

    /** @return the id of the deciding department manager, or {@code null} while PENDING */
    public Integer getDecidedBy() {
        return decidedBy;
    }

    /** @return the creation timestamp (string form), or {@code null} */
    public String getCreatedAt() {
        return createdAt;
    }

    /** @return the decision timestamp (string form), or {@code null} while PENDING */
    public String getDecidedAt() {
        return decidedAt;
    }
}
