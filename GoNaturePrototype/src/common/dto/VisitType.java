package common.dto;

/**
 * The nature of a park visit booked on a {@link ReservationDTO}.
 *
 * <p>The constant names match the {@code reservation.visit_type} SQL
 * {@code ENUM} values exactly so a row can be mapped with
 * {@code VisitType.valueOf(rs.getString("visit_type"))} and vice versa. Enums
 * are implicitly {@link java.io.Serializable}, so this type travels across the
 * socket inside a {@link ServerResponse} like any other DTO field.
 */
public enum VisitType {
    /** A single, unaccompanied visitor. */
    INDIVIDUAL,
    /** A family-sized booking under one visitor. */
    FAMILY,
    /** An organised group, typically led by a registered guide. */
    GROUP
}
