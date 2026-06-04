package common.dto;

/**
 * Lifecycle state of a {@link ReservationDTO}.
 *
 * <p>The constant names match the {@code reservation.status} SQL {@code ENUM}
 * values exactly so a row can be mapped with
 * {@code ReservationStatus.valueOf(rs.getString("status"))} and vice versa.
 * Enums are implicitly {@link java.io.Serializable}, so this type travels across
 * the socket inside a {@link ServerResponse} like any other DTO field.
 */
public enum ReservationStatus {
    /** Booked but not yet confirmed/paid. */
    PENDING,
    /** Confirmed and counted against park capacity. */
    CONFIRMED,
    /** Parked on the waiting list because capacity was full. */
    WAITING,
    /** Cancelled by the visitor or staff. */
    CANCELLED,
    /** The visit happened and was checked out. */
    COMPLETED,
    /** The visitor never showed up for a confirmed slot. */
    NO_SHOW
}
