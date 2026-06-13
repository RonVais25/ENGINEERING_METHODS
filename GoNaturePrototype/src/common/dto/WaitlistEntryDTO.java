package common.dto;

import java.io.Serializable;

/**
 * Immutable data transfer object representing a single waiting-list entry — a
 * visitor's queue position for a park/date that was full when they booked.
 *
 * <p>An entry is always paired with a {@link ReservationDTO} in
 * {@link ReservationStatus#WAITING}; the {@code waiting_list_entry} row carries
 * only the queueing/offer timestamps, while the booking details (which park,
 * which date, party size, owning visitor) live on the reservation. The four
 * convenience fields {@link #getParkId()}, {@link #getVisitDate()},
 * {@link #getPartySize()} and {@link #getVisitorId()} are {@code JOIN}-ed in from
 * that reservation by {@link server.dao.WaitlistDAO} so a single round-trip gives
 * a caller everything it needs to act on the entry (offer a grab, notify the
 * visitor) without re-fetching the reservation.
 *
 * <p>As elsewhere, the {@code DATETIME} columns are carried as strings
 * ({@code yyyy-MM-dd HH:mm:ss}) to keep {@code java.sql} types off the wire. The
 * two offer timestamps are {@code null} until the entry is offered a freed slot:
 * {@link #getGrabOfferedAt()}/{@link #getGrabExpiresAt()} are both set together by
 * {@link server.dao.WaitlistDAO#markOffered}, and a non-null pair with an expiry
 * still in the future marks an <em>active</em> grab offer.
 */
public class WaitlistEntryDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    /** Unique waiting-list entry identifier (primary key). */
    private int id;
    /** Identifier of the WAITING reservation this entry queues. */
    private int reservationId;
    /** When the visitor joined the queue (DB {@code DATETIME} as string). */
    private String queuedAt;
    /** When a freed slot was offered to this entry, or {@code null} if none is active. */
    private String grabOfferedAt;
    /** When the active grab offer lapses, or {@code null} if none is active. */
    private String grabExpiresAt;

    /** Park the queued reservation is for (joined from the reservation). */
    private int parkId;
    /** Visit date the queued reservation is for, ISO {@code yyyy-MM-dd} (joined). */
    private String visitDate;
    /** Party size of the queued reservation (joined). */
    private int partySize;
    /** National-ID-style identifier of the owning visitor (joined). */
    private long visitorId;

    /**
     * Creates a fully populated waiting-list entry.
     *
     * @param id            unique waiting-list entry identifier
     * @param reservationId identifier of the queued WAITING reservation
     * @param queuedAt      when the visitor joined the queue (string {@code DATETIME})
     * @param grabOfferedAt when a freed slot was offered, or {@code null}
     * @param grabExpiresAt when the active offer lapses, or {@code null}
     * @param parkId        park the reservation is for (joined)
     * @param visitDate     visit date the reservation is for, ISO {@code yyyy-MM-dd} (joined)
     * @param partySize     party size of the reservation (joined)
     * @param visitorId     identifier of the owning visitor (joined)
     */
    public WaitlistEntryDTO(int id, int reservationId, String queuedAt, String grabOfferedAt,
                            String grabExpiresAt, int parkId, String visitDate, int partySize,
                            long visitorId) {
        this.id = id;
        this.reservationId = reservationId;
        this.queuedAt = queuedAt;
        this.grabOfferedAt = grabOfferedAt;
        this.grabExpiresAt = grabExpiresAt;
        this.parkId = parkId;
        this.visitDate = visitDate;
        this.partySize = partySize;
        this.visitorId = visitorId;
    }

    /** @return the unique waiting-list entry identifier */
    public int getId() {
        return id;
    }

    /** @return the identifier of the queued WAITING reservation */
    public int getReservationId() {
        return reservationId;
    }

    /** @return when the visitor joined the queue (string {@code DATETIME}) */
    public String getQueuedAt() {
        return queuedAt;
    }

    /** @return when a freed slot was offered to this entry, or {@code null} if none is active */
    public String getGrabOfferedAt() {
        return grabOfferedAt;
    }

    /** @return when the active grab offer lapses, or {@code null} if none is active */
    public String getGrabExpiresAt() {
        return grabExpiresAt;
    }

    /** @return the park the queued reservation is for (joined from the reservation) */
    public int getParkId() {
        return parkId;
    }

    /** @return the visit date the queued reservation is for, ISO {@code yyyy-MM-dd} (joined) */
    public String getVisitDate() {
        return visitDate;
    }

    /** @return the party size of the queued reservation (joined) */
    public int getPartySize() {
        return partySize;
    }

    /** @return the identifier of the owning visitor (joined) */
    public long getVisitorId() {
        return visitorId;
    }
}
