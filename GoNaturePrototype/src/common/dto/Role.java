package common.dto;

/**
 * Staff role of a {@code user} row. The constant names match the
 * {@code user.role} {@code ENUM} in {@code setup.sql} exactly so a column value
 * can be parsed straight through {@link #valueOf(String)}.
 */
public enum Role {
    /** Front-line park employee. */
    PARK_EMPLOYEE,
    /** Manager of a single park. */
    PARK_MANAGER,
    /** Department manager overseeing all parks. */
    DEPT_MANAGER,
    /** Customer service representative. */
    SERVICE_REP
}
