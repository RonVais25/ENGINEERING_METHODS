package common.dto;

import java.io.Serializable;

/**
 * Result of a successful {@link RequestType#UPDATE_RESERVATION}: the freshly
 * persisted {@link ReservationDTO} bundled with the price before and after the
 * edit, so the client can settle the difference.
 *
 * <p>Rescheduling can change the party size and therefore the price. The server
 * recomputes {@code price_cents} as part of the update and reports both the
 * {@code oldPriceCents} and the {@code newPriceCents} here; the client derives
 * {@code delta = newPriceCents - oldPriceCents} to decide whether to collect the
 * difference (prepaid, price up), refund it (prepaid, price down) or simply quote
 * the new total due at the gate (pay-on-arrival). The updated reservation already
 * carries the new price and the {@code paidInAdvance} flag the settlement wording
 * depends on.
 *
 * <p>Carried in {@link ServerResponse#getData()} for the update reply only. The
 * realtime {@link ServerEvent} push still carries the plain {@link ReservationDTO},
 * since subscribers refresh from the new row and do not settle.
 */
public class ReservationUpdateResultDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    /** The reservation as persisted after the edit (carries the new price). */
    private final ReservationDTO reservation;
    /** The reservation's price before the edit, in cents. */
    private final int oldPriceCents;
    /** The reservation's recomputed price after the edit, in cents. */
    private final int newPriceCents;

    /**
     * Creates an update result.
     *
     * @param reservation   the reservation as persisted after the edit
     * @param oldPriceCents the price before the edit, in cents
     * @param newPriceCents the recomputed price after the edit, in cents
     */
    public ReservationUpdateResultDTO(ReservationDTO reservation, int oldPriceCents, int newPriceCents) {
        this.reservation = reservation;
        this.oldPriceCents = oldPriceCents;
        this.newPriceCents = newPriceCents;
    }

    /** @return the reservation as persisted after the edit */
    public ReservationDTO getReservation() {
        return reservation;
    }

    /** @return the price before the edit, in cents */
    public int getOldPriceCents() {
        return oldPriceCents;
    }

    /** @return the recomputed price after the edit, in cents */
    public int getNewPriceCents() {
        return newPriceCents;
    }

    /** @return the price change, in cents (positive = increase, negative = refund). */
    public int getDeltaCents() {
        return newPriceCents - oldPriceCents;
    }
}
