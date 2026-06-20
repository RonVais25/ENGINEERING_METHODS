package common.dto;

import java.io.Serializable;

/**
 * Immutable data transfer object representing a single {@code visit} row — one
 * party's actual entry into a park (by reservation or as a casual walk-in).
 *
 * <p>Created server-side by {@link server.dao.VisitDAO} from a database row and
 * serialized across the socket to the client inside a {@link ServerResponse}
 * (e.g. the gate screen echoing the visit just opened/closed). As with
 * {@link ReservationDTO}, the {@code DATETIME} columns are carried as strings
 * ({@code yyyy-MM-dd HH:mm:ss}) so the wire format is not coupled to
 * {@code java.sql} types.
 *
 * <p>Columns that are nullable in the database are modelled with boxed types so a
 * SQL {@code NULL} maps to a Java {@code null} rather than a misleading zero:
 * <ul>
 *   <li>{@code reservationId} — {@code null} for a casual walk-in;</li>
 *   <li>{@code visitorId} — {@code null} for an anonymous casual party;</li>
 *   <li>{@code exitedAt} — {@code null} while the party is still inside;</li>
 *   <li>{@code priceCents} / {@code visitType} — persisted only for casual
 *       visits; {@code null} on a reservation visit (derive them from the
 *       reservation instead).</li>
 * </ul>
 */
public class VisitDTO implements Serializable {
/** Serialization identifier for this class. */
    private static final long serialVersionUID = 1L;

    /** Unique visit identifier (primary key). */
    private int id;
    /** Identifier of the backing reservation, or {@code null} for a casual walk-in. */
    private Integer reservationId;
    /** Identifier of the park entered. */
    private int parkId;
    /** National-ID-style identifier of the visitor, or {@code null} if anonymous. */
    private Long visitorId;
    /** Entry timestamp (DB {@code DATETIME} as string). */
    private String enteredAt;
    /** Exit timestamp (DB {@code DATETIME} as string), or {@code null} while still inside. */
    private String exitedAt;
    /** Number of people in the party counted against occupancy. */
    private int headcount;
    /** Price charged in cents (casual visits only), or {@code null} for a reservation visit. */
    private Integer priceCents;
    /** Nature of the visit (casual visits only), or {@code null} for a reservation visit. */
    private VisitType visitType;

    /**
     * Creates a fully populated visit.
     *
     * @param id            unique visit identifier
     * @param reservationId identifier of the backing reservation, or {@code null} if casual
     * @param parkId        identifier of the park entered
     * @param visitorId     identifier of the visitor, or {@code null} if anonymous
     * @param enteredAt     entry timestamp as a string
     * @param exitedAt      exit timestamp as a string, or {@code null} while still inside
     * @param headcount     number of people in the party
     * @param priceCents    price charged in cents (casual only), or {@code null}
     * @param visitType     nature of the visit (casual only), or {@code null}
     */
    public VisitDTO(int id, Integer reservationId, int parkId, Long visitorId, String enteredAt,
                    String exitedAt, int headcount, Integer priceCents, VisitType visitType) {
        this.id = id;
        this.reservationId = reservationId;
        this.parkId = parkId;
        this.visitorId = visitorId;
        this.enteredAt = enteredAt;
        this.exitedAt = exitedAt;
        this.headcount = headcount;
        this.priceCents = priceCents;
        this.visitType = visitType;
    }

    /** @return the unique visit identifier */
    public int getId() {
        return id;
    }

    /** @return the identifier of the backing reservation, or {@code null} for a casual walk-in */
    public Integer getReservationId() {
        return reservationId;
    }

    /** @return the identifier of the park entered */
    public int getParkId() {
        return parkId;
    }

    /** @return the identifier of the visitor, or {@code null} if anonymous */
    public Long getVisitorId() {
        return visitorId;
    }

    /** @return the entry timestamp as a string */
    public String getEnteredAt() {
        return enteredAt;
    }

    /** @return the exit timestamp as a string, or {@code null} while the party is still inside */
    public String getExitedAt() {
        return exitedAt;
    }

    /** @return the number of people in the party */
    public int getHeadcount() {
        return headcount;
    }

    /** @return the price charged in cents (casual visits only), or {@code null} for a reservation visit */
    public Integer getPriceCents() {
        return priceCents;
    }

    /** @return the nature of the visit (casual visits only), or {@code null} for a reservation visit */
    public VisitType getVisitType() {
        return visitType;
    }
}
