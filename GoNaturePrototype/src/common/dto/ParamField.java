package common.dto;

/**
 * The park parameter targeted by a {@code parameter_change_request}.
 *
 * <p>The constant names match the {@code parameter_change_request.field}
 * {@code ENUM} in {@code setup.sql} exactly, so a column value can be parsed
 * straight through {@link #valueOf(String)} and a constant written back with
 * {@link #name()}.
 */
public enum ParamField {
    /** The park's maximum visitor capacity ({@code park.max_capacity}). */
    MAX_CAPACITY,
    /** The reserved capacity buffer held back from booking ({@code park.gap_size}). */
    GAP_SIZE,
    /** The default visit length in minutes ({@code park.default_stay_minutes}). */
    DEFAULT_STAY_MINUTES,
    /** Additional special-sale discount percent applied after department approval. */
    SPECIAL_DISCOUNT_PERCENT
}
