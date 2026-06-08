package common.dto;

/**
 * Lifecycle status of a {@code parameter_change_request}.
 *
 * <p>The constant names match the {@code parameter_change_request.status}
 * {@code ENUM} in {@code setup.sql} exactly, so a column value can be parsed
 * straight through {@link #valueOf(String)}.
 */
public enum ChangeStatus {
    /** Submitted by a park manager, awaiting a department-manager decision. */
    PENDING,
    /** Approved by a department manager; the new value has been written to the park. */
    APPROVED,
    /** Rejected by a department manager; the park was left unchanged. */
    REJECTED
}
