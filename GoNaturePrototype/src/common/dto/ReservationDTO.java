package common.dto;

import java.io.Serializable;

/**
 * Immutable data transfer object representing a single park reservation.
 *
 * <p>Instances are created server-side by {@link server.dao.ReservationDAO} from a
 * database row and serialized across the socket to the client inside a
 * {@link common.dto.ServerResponse}. As with {@link OrderDTO}, date/time fields are
 * carried as strings (ISO {@code yyyy-MM-dd} for the visit date) to avoid coupling
 * the wire format to {@code java.sql} types.
 *
 * <p>Columns that are nullable in the database ({@code guide_id},
 * {@code confirmation_code}) are modelled with boxed {@link Long}/{@link Integer}
 * so a SQL {@code NULL} maps to a Java {@code null} rather than a misleading zero.
 */
public class ReservationDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    /** Unique reservation identifier (primary key). */
    private int id;
    /** Identifier of the park this reservation is booked against. */
    private int parkId;
    /** National-ID-style identifier of the owning visitor. */
    private long visitorId;
    /** Scheduled visit date, ISO {@code yyyy-MM-dd}. */
    private String visitDate;
    /** Scheduled visit time ({@code HH:mm:ss}), or {@code null} if unset. */
    private String visitTime;
    /** Number of people in the party. */
    private int partySize;
    /** Nature of the visit (individual/family/group). */
    private VisitType visitType;
    /** Current lifecycle state of the reservation. */
    private ReservationStatus status;
    /** Whether this booking is treated as an organised group. */
    private boolean isGroup;
    /** Identifier of the assigned guide, or {@code null} if none. */
    private Long guideId;
    /** Price of the reservation, in cents. */
    private int priceCents;
    /** Whether the reservation was paid for in advance. */
    private boolean paidInAdvance;
    /** Booking confirmation code, or {@code null} if not yet issued. */
    private Integer confirmationCode;
    /** Timestamp the reservation row was created (DB {@code DATETIME} as string). */
    private String createdAt;

    /**
     * Creates a fully populated reservation.
     *
     * @param id               unique reservation identifier
     * @param parkId           identifier of the booked park
     * @param visitorId        identifier of the owning visitor
     * @param visitDate        scheduled visit date, ISO {@code yyyy-MM-dd}
     * @param visitTime        scheduled visit time ({@code HH:mm:ss}), or {@code null}
     * @param partySize        number of people in the party
     * @param visitType        nature of the visit
     * @param status           current lifecycle state
     * @param isGroup          whether the booking is an organised group
     * @param guideId          identifier of the assigned guide, or {@code null}
     * @param priceCents       price of the reservation, in cents
     * @param paidInAdvance    whether the reservation was paid in advance
     * @param confirmationCode booking confirmation code, or {@code null}
     * @param createdAt        creation timestamp as a string
     */
    public ReservationDTO(int id, int parkId, long visitorId, String visitDate, String visitTime,
                          int partySize, VisitType visitType, ReservationStatus status, boolean isGroup,
                          Long guideId, int priceCents, boolean paidInAdvance, Integer confirmationCode,
                          String createdAt) {
        this.id = id;
        this.parkId = parkId;
        this.visitorId = visitorId;
        this.visitDate = visitDate;
        this.visitTime = visitTime;
        this.partySize = partySize;
        this.visitType = visitType;
        this.status = status;
        this.isGroup = isGroup;
        this.guideId = guideId;
        this.priceCents = priceCents;
        this.paidInAdvance = paidInAdvance;
        this.confirmationCode = confirmationCode;
        this.createdAt = createdAt;
    }

    /** @return the unique reservation identifier */
    public int getId() {
        return id;
    }

    /** @return the identifier of the booked park */
    public int getParkId() {
        return parkId;
    }

    /** @return the identifier of the owning visitor */
    public long getVisitorId() {
        return visitorId;
    }

    /** @return the scheduled visit date, ISO {@code yyyy-MM-dd} */
    public String getVisitDate() {
        return visitDate;
    }

    /** @return the scheduled visit time ({@code HH:mm:ss}), or {@code null} if unset */
    public String getVisitTime() {
        return visitTime;
    }

    /** @return the number of people in the party */
    public int getPartySize() {
        return partySize;
    }

    /** @return the nature of the visit */
    public VisitType getVisitType() {
        return visitType;
    }

    /** @return the current lifecycle state */
    public ReservationStatus getStatus() {
        return status;
    }

    /** @return whether the booking is treated as an organised group */
    public boolean isGroup() {
        return isGroup;
    }

    /** @return the identifier of the assigned guide, or {@code null} if none */
    public Long getGuideId() {
        return guideId;
    }

    /** @return the price of the reservation, in cents */
    public int getPriceCents() {
        return priceCents;
    }

    /** @return whether the reservation was paid for in advance */
    public boolean isPaidInAdvance() {
        return paidInAdvance;
    }

    /** @return the booking confirmation code, or {@code null} if not yet issued */
    public Integer getConfirmationCode() {
        return confirmationCode;
    }

    /** @return the creation timestamp as a string */
    public String getCreatedAt() {
        return createdAt;
    }
}
