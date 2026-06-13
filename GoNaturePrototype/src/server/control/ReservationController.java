package server.control;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

import common.dto.ClientRequest;
import common.dto.ParkDTO;
import common.dto.ReservationDTO;
import common.dto.ReservationStatus;
import common.dto.RequestType;
import common.dto.ServerEvent;
import common.dto.ServerResponse;
import common.dto.SubscriptionKey;
import common.dto.VisitType;
import common.dto.VisitorDTO;
import common.dto.WaitlistEntryDTO;
import server.dao.AuthDAO;
import server.dao.ParkDAO;
import server.dao.ReservationDAO;
import server.dao.WaitlistDAO;
import server.net.ClientSession;
import server.subscription.SubscriptionRegistry;

import static common.dto.RequestType.ACCEPT_GRAB;
import static common.dto.RequestType.CANCEL_RESERVATION;
import static common.dto.RequestType.CONFIRM_RESERVATION;
import static common.dto.RequestType.CREATE_RESERVATION;
import static common.dto.RequestType.GET_RESERVATION;
import static common.dto.RequestType.JOIN_WAITLIST;
import static common.dto.RequestType.LEAVE_WAITLIST;
import static common.dto.RequestType.LIST_RESERVATIONS;
import static common.dto.RequestType.UPDATE_RESERVATION;

/**
 * Owns the reservation &amp; waiting-list domain (create/get/update/cancel/list,
 * confirm, join/leave waitlist, accept grab). Stateless and shared across all
 * client threads — only the final {@link ReservationDAO} collaborator is held as
 * state.
 *
 * <p>Implemented: the read ops ({@link RequestType#GET_RESERVATION},
 * {@link RequestType#LIST_RESERVATIONS}), {@link RequestType#CREATE_RESERVATION}
 * (INDIVIDUAL/FAMILY and guide-led GROUP), the
 * {@link RequestType#CANCEL_RESERVATION}/{@link RequestType#CONFIRM_RESERVATION}
 * lifecycle ops, {@link RequestType#UPDATE_RESERVATION} (reschedule), and the
 * waiting-list ops ({@link RequestType#JOIN_WAITLIST}/{@link RequestType#LEAVE_WAITLIST}/
 * {@link RequestType#ACCEPT_GRAB}). Status transitions are gated by
 * {@link #isLegalTransition} so the rules live in one place and are enforced
 * server-side regardless of the client UI.
 *
 * <p><strong>Waiting list.</strong> A waitlist entry <em>is</em> a reservation in
 * {@link ReservationStatus#WAITING} plus a {@code waiting_list_entry} row; WAITING
 * reservations do not consume capacity (see
 * {@link ReservationDAO#availableCapacity}). When a PENDING/CONFIRMED reservation
 * is cancelled, {@link #offerGrabToNext} offers the freed slot to the FIFO-first
 * waiting party that fits and gives them a one-hour window to {@code ACCEPT_GRAB}
 * (flip to CONFIRMED). Declining ({@code LEAVE_WAITLIST} during an active offer)
 * or letting the window lapse ({@link #expireOverdueOffers}) advances the offer to
 * the next eligible party. The expiry sweep is timer-free here — a later Scheduler
 * session drives it on an interval.
 *
 * <p><strong>Realtime push.</strong> After each successful mutation the controller
 * re-fetches the persisted row and broadcasts a {@link ServerEvent} for entity
 * {@code "reservation"} (id = reservation id) through
 * {@link SubscriptionRegistry}, reusing the exact mechanism the order domain uses
 * — see {@link #publishReservation}. Subscribers get the committed row.
 */
public class ReservationController implements DomainController {

    private final ReservationDAO dao = new ReservationDAO();
    private final AuthDAO authDao = new AuthDAO();
    /** Stateless price calculator, shared across all client threads. */
    private final PricingService pricing = new PricingService();
    /** Stateless notification helper, shared across all client threads. */
    private final NotificationService notificationService = new NotificationService();
    /** Stateless waiting-list DAO collaborator, shared across all client threads. */
    private final WaitlistDAO waitlistDao = new WaitlistDAO();
    /** Stateless park DAO, used here only to resolve a park's name for grab notifications. */
    private final ParkDAO parkDao = new ParkDAO();

    /** Format for the {@code grab_expires_at} deadline strings handed to {@link WaitlistDAO}. */
    private static final DateTimeFormatter SQL_DATETIME =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /** How long an offered grab stays claimable before it lapses. */
    private static final int GRAB_TTL_HOURS = 1;

    @Override
    public Set<RequestType> handledTypes() {
        return Set.of(CREATE_RESERVATION, GET_RESERVATION, UPDATE_RESERVATION, CANCEL_RESERVATION,
                LIST_RESERVATIONS, CONFIRM_RESERVATION, JOIN_WAITLIST, LEAVE_WAITLIST, ACCEPT_GRAB);
    }

    @Override
    public ServerResponse handle(ClientRequest request, ClientSession session) {

        switch (request.getType()) {

            case GET_RESERVATION: {
                int id = (int) request.get("reservationId");

                ReservationDTO reservation = dao.getById(id);

                if (reservation != null) {
                    return new ServerResponse(true, "Reservation found.", reservation);
                }

                return new ServerResponse(false, "Reservation not found.");
            }

            case LIST_RESERVATIONS: {
                long visitorId = ((Number) request.get("visitorId")).longValue();

                List<ReservationDTO> reservations = dao.findByVisitor(visitorId);

                return new ServerResponse(true, "Reservations listed.", reservations);
            }

            case CREATE_RESERVATION: {
                int       parkId    = (int) request.get("parkId");
                long      visitorId = ((Number) request.get("visitorId")).longValue();
                String    visitDate = (String) request.get("visitDate");
                String    visitTime = (String) request.get("visitTime"); // nullable
                int       partySize = (int) request.get("partySize");
                VisitType visitType = (VisitType) request.get("visitType");

                boolean isGroup = (visitType == VisitType.GROUP);
                Long    guideId = null;

                // Group visits are guide-led and capped at 15 people. The guide
                // must be a registered guide (row in the guide table).
                if (isGroup) {
                    if (partySize > 15) {
                        return new ServerResponse(false, "Group size cannot exceed 15.");
                    }
                    Object rawGuide = request.get("guideId");
                    if (rawGuide == null) {
                        return new ServerResponse(false, "A guide is required for group bookings.");
                    }
                    guideId = ((Number) rawGuide).longValue();
                    if (!dao.guideExists(guideId)) {
                        return new ServerResponse(false, "No registered guide with id " + guideId + ".");
                    }
                }

                // Capacity gate. The waiting-list offer on overflow is a Phase 4
                // feature; for now an over-capacity request is simply rejected.
                int free = dao.availableCapacity(parkId, visitDate);
                if (partySize > free) {
                    return new ServerResponse(false,
                            "No capacity for that date (free: " + free + ").");
                }

                int confirmationCode = ThreadLocalRandom.current().nextInt(1000, 10000);

                // A booking is always pre-ordered. Whether the visitor opts to pay
                // up front (deepens the group discount) comes from the request; the
                // member discount comes from the visitor's subscription flag.
                Object  rawPrePaid = request.get("paidInAdvance");
                boolean prePaid    = rawPrePaid != null && (Boolean) rawPrePaid;

                VisitorDTO visitor  = authDao.findVisitorById(visitorId);
                boolean    isMember = visitor != null && visitor.isSubscriber();

                int price = pricing.calculate(visitType, isGroup, partySize, true, prePaid, isMember);

                ReservationDTO toInsert = new ReservationDTO(
                        0,                        // id assigned by the DB
                        parkId,
                        visitorId,
                        visitDate,
                        visitTime,
                        partySize,
                        visitType,
                        ReservationStatus.PENDING,
                        isGroup,
                        guideId,                  // the validated guide, or null for non-group
                        price,                    // computed by PricingService
                        prePaid,                  // paidInAdvance, from the request
                        confirmationCode,
                        null);                    // createdAt — DB default fills it

                int newId = dao.insert(toInsert);
                if (newId < 0) {
                    return new ServerResponse(false, "Could not create reservation.");
                }

                ReservationDTO created = dao.getById(newId);
                // Broadcast the new row to any client subscribed to this id.
                publishReservation(ServerEvent.created("reservation", newId, created));
                return new ServerResponse(true,
                        "Reservation created (confirmation code: " + confirmationCode + ").",
                        created);
            }

            case CONFIRM_RESERVATION: {
                int id = (int) request.get("reservationId");

                ReservationDTO existing = dao.getById(id);
                if (existing == null) {
                    return new ServerResponse(false, "Reservation not found.");
                }
                if (!isLegalTransition(existing.getStatus(), ReservationStatus.CONFIRMED)) {
                    return new ServerResponse(false,
                            "Cannot confirm a reservation that is " + existing.getStatus()
                            + " (only PENDING reservations can be confirmed).");
                }
                if (!dao.updateStatus(id, ReservationStatus.CONFIRMED)) {
                    return new ServerResponse(false, "Confirm failed.");
                }
                // Re-fetch the persisted row (mirrors OrderController) so subscribers
                // get exactly what was committed, then broadcast.
                ReservationDTO confirmed = dao.getById(id);
                publishReservation(ServerEvent.updated("reservation", id, confirmed));
                // Notify the visitor their booking is confirmed. Persisted + pushed
                // if they are online; otherwise it waits in their notification center.
                notificationService.send(confirmed.getVisitorId(), null, "SIM_EMAIL",
                        "Your reservation #" + id + " was confirmed.");
                return new ServerResponse(true, "Reservation confirmed.", confirmed);
            }

            case CANCEL_RESERVATION: {
                int id = (int) request.get("reservationId");

                ReservationDTO existing = dao.getById(id);
                if (existing == null) {
                    return new ServerResponse(false, "Reservation not found.");
                }
                if (!isLegalTransition(existing.getStatus(), ReservationStatus.CANCELLED)) {
                    return new ServerResponse(false,
                            "Cannot cancel a reservation that is " + existing.getStatus() + ".");
                }
                if (!dao.updateStatus(id, ReservationStatus.CANCELLED)) {
                    return new ServerResponse(false, "Cancel failed.");
                }
                ReservationDTO cancelled = dao.getById(id);
                publishReservation(ServerEvent.updated("reservation", id, cancelled));
                // Notify the visitor their booking was cancelled (push if online,
                // else fetched from their notification center on next login).
                notificationService.send(cancelled.getVisitorId(), null, "SIM_EMAIL",
                        "Your reservation #" + id + " was cancelled.");
                // Cancelling a PENDING/CONFIRMED booking frees a real slot — offer the
                // grab to the FIFO-first waiting party that fits this park/date. (A
                // no-op when the cancelled row was itself WAITING: it freed nothing.)
                offerGrabToNext(cancelled.getParkId(), cancelled.getVisitDate());
                return new ServerResponse(true, "Reservation cancelled.", cancelled);
            }

            case UPDATE_RESERVATION: {
                int    id        = (int) request.get("reservationId");
                String visitDate = (String) request.get("visitDate");
                String visitTime = (String) request.get("visitTime"); // nullable
                int    partySize = (int) request.get("partySize");

                ReservationDTO existing = dao.getById(id);
                if (existing == null) {
                    return new ServerResponse(false, "Reservation not found.");
                }
                // Only an active (PENDING/CONFIRMED) reservation can be rescheduled.
                ReservationStatus st = existing.getStatus();
                if (st != ReservationStatus.PENDING && st != ReservationStatus.CONFIRMED) {
                    return new ServerResponse(false, "Cannot modify a reservation that is " + st + ".");
                }
                if (existing.isGroup() && partySize > 15) {
                    return new ServerResponse(false, "Group size cannot exceed 15.");
                }

                // Capacity re-check for the (possibly new) date. availableCapacity
                // already counts this reservation's current party when it is
                // PENDING/CONFIRMED on that same date, so add it back to compare
                // the NEW size against capacity excluding this reservation.
                int free = dao.availableCapacity(existing.getParkId(), visitDate);
                int effectiveFree = existing.getVisitDate().equals(visitDate)
                        ? free + existing.getPartySize()
                        : free;
                if (partySize > effectiveFree) {
                    return new ServerResponse(false,
                            "No capacity for that date (free: " + Math.max(effectiveFree, 0) + ").");
                }

                if (!dao.updateDateAndParty(id, visitDate, visitTime, partySize)) {
                    return new ServerResponse(false, "Update failed.");
                }
                ReservationDTO updated = dao.getById(id);
                publishReservation(ServerEvent.updated("reservation", id, updated));
                return new ServerResponse(true, "Reservation updated.", updated);
            }

            case JOIN_WAITLIST: {
                int       parkId    = (int) request.get("parkId");
                long      visitorId = ((Number) request.get("visitorId")).longValue();
                String    visitDate = (String) request.get("visitDate");
                String    visitTime = (String) request.get("visitTime"); // nullable
                int       partySize = (int) request.get("partySize");
                VisitType visitType = (VisitType) request.get("visitType");

                boolean isGroup = (visitType == VisitType.GROUP);
                Long    guideId = null;

                // Same group rules as a booking (guide-led, capped at 15). These are
                // domain invariants independent of capacity, so a waitlisted group
                // still needs its guide for when the slot is eventually grabbed.
                if (isGroup) {
                    if (partySize > 15) {
                        return new ServerResponse(false, "Group size cannot exceed 15.");
                    }
                    Object rawGuide = request.get("guideId");
                    if (rawGuide == null) {
                        return new ServerResponse(false, "A guide is required for group bookings.");
                    }
                    guideId = ((Number) rawGuide).longValue();
                    if (!dao.guideExists(guideId)) {
                        return new ServerResponse(false, "No registered guide with id " + guideId + ".");
                    }
                }

                // No capacity gate: a visitor joins the waiting list precisely because
                // the date is full. A WAITING reservation does not consume capacity
                // (availableCapacity counts only PENDING/CONFIRMED), so it simply
                // parks here until a freed slot is offered to it.
                Object  rawPrePaid = request.get("paidInAdvance");
                boolean prePaid    = rawPrePaid != null && (Boolean) rawPrePaid;

                VisitorDTO visitor  = authDao.findVisitorById(visitorId);
                boolean    isMember = visitor != null && visitor.isSubscriber();

                int price = pricing.calculate(visitType, isGroup, partySize, true, prePaid, isMember);
                // Issue the confirmation code up front, exactly as CREATE_RESERVATION
                // does, so the booking is gate-usable the moment it is grabbed.
                int confirmationCode = ThreadLocalRandom.current().nextInt(1000, 10000);

                ReservationDTO toInsert = new ReservationDTO(
                        0,                        // id assigned by the DB
                        parkId,
                        visitorId,
                        visitDate,
                        visitTime,
                        partySize,
                        visitType,
                        ReservationStatus.WAITING,
                        isGroup,
                        guideId,                  // the validated guide, or null for non-group
                        price,                    // computed by PricingService (preOrdered)
                        prePaid,
                        confirmationCode,
                        null);                    // createdAt — DB default fills it

                int reservationId = dao.insert(toInsert);
                if (reservationId < 0) {
                    return new ServerResponse(false, "Could not join the waiting list.");
                }
                int entryId = waitlistDao.insertEntry(reservationId);
                if (entryId < 0) {
                    return new ServerResponse(false, "Could not join the waiting list.");
                }

                ReservationDTO created = dao.getById(reservationId);
                WaitlistEntryDTO entry = waitlistDao.getById(entryId);
                // Broadcast the new WAITING reservation like any other new row.
                publishReservation(ServerEvent.created("reservation", reservationId, created));
                return new ServerResponse(true,
                        "Added to the waiting list for that date.", entry);
            }

            case ACCEPT_GRAB: {
                WaitlistEntryDTO entry = resolveEntry(request);
                if (entry == null) {
                    return new ServerResponse(false, "No waiting-list entry found.");
                }
                // Must hold a live offer: offered, with an expiry still in the future.
                if (entry.getGrabOfferedAt() == null || !isOfferActive(entry)) {
                    return new ServerResponse(false,
                            "No active grab offer for this entry (it may have expired).");
                }

                ReservationDTO reservation = dao.getById(entry.getReservationId());
                if (reservation == null || reservation.getStatus() != ReservationStatus.WAITING) {
                    return new ServerResponse(false,
                            "This waiting-list reservation is no longer claimable.");
                }

                // Safety re-check: capacity could have been taken since the offer was
                // made, so confirm the offered slot still fits this party.
                int free = dao.availableCapacity(reservation.getParkId(), reservation.getVisitDate());
                if (reservation.getPartySize() > free) {
                    return new ServerResponse(false,
                            "The slot was just taken — not enough capacity to confirm.");
                }

                if (!dao.updateStatus(reservation.getId(), ReservationStatus.CONFIRMED)) {
                    return new ServerResponse(false, "Confirm failed.");
                }
                waitlistDao.removeEntry(entry.getId());

                ReservationDTO confirmed = dao.getById(reservation.getId());
                publishReservation(ServerEvent.updated("reservation", confirmed.getId(), confirmed));
                notificationService.send(confirmed.getVisitorId(), null, "SIM_EMAIL",
                        "Your waiting-list reservation #" + confirmed.getId() + " was confirmed.");
                return new ServerResponse(true, "Grab accepted — reservation confirmed.", confirmed);
            }

            case LEAVE_WAITLIST: {
                WaitlistEntryDTO entry = resolveEntry(request);
                if (entry == null) {
                    return new ServerResponse(false, "No waiting-list entry found.");
                }
                ReservationDTO reservation = dao.getById(entry.getReservationId());
                if (reservation == null) {
                    return new ServerResponse(false, "Reservation not found.");
                }
                if (reservation.getStatus() != ReservationStatus.WAITING) {
                    return new ServerResponse(false, "That reservation is not on the waiting list.");
                }

                // Leaving while holding a live offer is a decline: the offered slot is
                // still free, so after removing this entry we advance the grab to the
                // next eligible party. A plain leave frees nothing — a WAITING
                // reservation never consumed capacity. Capture park/date before the
                // row is mutated.
                boolean wasDeclining = entry.getGrabOfferedAt() != null && isOfferActive(entry);
                int     parkId    = reservation.getParkId();
                String  visitDate = reservation.getVisitDate();

                if (!dao.updateStatus(reservation.getId(), ReservationStatus.CANCELLED)) {
                    return new ServerResponse(false, "Leave failed.");
                }
                waitlistDao.removeEntry(entry.getId());

                ReservationDTO left = dao.getById(reservation.getId());
                publishReservation(ServerEvent.updated("reservation", left.getId(), left));
                if (wasDeclining) {
                    offerGrabToNext(parkId, visitDate);
                }
                return new ServerResponse(true, "Removed from the waiting list.", left);
            }

            default:
                return new ServerResponse(false, "NOT_IMPLEMENTED: " + request.getType());
        }
    }

    /**
     * The reservation state machine: whether a reservation may move from its
     * {@code current} status to {@code target}. Single source of truth for the
     * lifecycle ops (CONFIRM/CANCEL today, more later), enforced here on the
     * server so a misbehaving or out-of-date client cannot drive an illegal
     * transition by skipping its own disabled-button checks.
     *
     * <p>Legal transitions:
     * <ul>
     *   <li>{@code PENDING}   → CONFIRMED or CANCELLED</li>
     *   <li>{@code CONFIRMED} → CANCELLED</li>
     *   <li>{@code WAITING}   → CANCELLED</li>
     *   <li>{@code CANCELLED}, {@code COMPLETED}, {@code NO_SHOW} → none (terminal)</li>
     * </ul>
     *
     * @param current the reservation's present status
     * @param target  the status the caller wants to apply
     * @return {@code true} if the transition is permitted
     */
    private boolean isLegalTransition(ReservationStatus current, ReservationStatus target) {
        switch (current) {
            case PENDING:   return target == ReservationStatus.CONFIRMED
                                || target == ReservationStatus.CANCELLED;
            case CONFIRMED: return target == ReservationStatus.CANCELLED;
            case WAITING:   return target == ReservationStatus.CANCELLED;
            default:        return false; // CANCELLED / COMPLETED / NO_SHOW are terminal
        }
    }

    /**
     * Resolves the waiting-list entry a request targets. Accepts either a
     * {@code reservationId} (the handle the client already holds for the booking)
     * or, as a fallback, a direct {@code entryId}.
     *
     * @param request the client request carrying {@code reservationId} or {@code entryId}
     * @return the matching {@link WaitlistEntryDTO}, or {@code null} if neither key resolves
     */
    private WaitlistEntryDTO resolveEntry(ClientRequest request) {
        Object rawReservationId = request.get("reservationId");
        if (rawReservationId != null) {
            return waitlistDao.getByReservation((int) rawReservationId);
        }
        Object rawEntryId = request.get("entryId");
        if (rawEntryId != null) {
            return waitlistDao.getById((int) rawEntryId);
        }
        return null;
    }

    /**
     * Whether an entry's grab offer is still live: it has been offered and its
     * expiry is in the future. Compared against the JVM clock — the same clock the
     * expiry is computed from in {@link #offerGrabToNext} — so the window is
     * internally consistent.
     *
     * @param entry the entry to test (assumed to have a non-null {@code grabOfferedAt})
     * @return {@code true} if the offer has not yet lapsed
     */
    private boolean isOfferActive(WaitlistEntryDTO entry) {
        String expires = entry.getGrabExpiresAt();
        return expires != null
                && Timestamp.valueOf(expires).after(new Timestamp(System.currentTimeMillis()));
    }

    /**
     * Offers a freed slot to the next waiting visitor. Recomputes the live capacity
     * for the park/date, finds the FIFO-first WAITING entry whose party fits (and
     * that does not already hold an offer), marks it offered for
     * {@link #GRAB_TTL_HOURS} hour(s), and notifies that visitor. A no-op when no
     * capacity is free or no eligible entry is waiting (e.g. the head party is too
     * large to fit the freed slot — the must-fit rule skips it rather than blocking
     * the queue).
     *
     * <p>Called after any event that frees a real slot: a cancelled
     * PENDING/CONFIRMED reservation, a declined offer, or a lapsed offer.
     *
     * @param parkId    the park a slot freed up for
     * @param visitDate the visit date a slot freed up for, ISO {@code yyyy-MM-dd}
     */
    private void offerGrabToNext(int parkId, String visitDate) {
        int cap = dao.availableCapacity(parkId, visitDate);
        if (cap <= 0) {
            return; // nothing freed up (or overbooked) — no slot to offer
        }
        WaitlistEntryDTO next = waitlistDao.findNextEligible(parkId, visitDate, cap);
        if (next == null) {
            return; // queue empty, or every waiting party is larger than the freed slot
        }
        String expires = LocalDateTime.now().plusHours(GRAB_TTL_HOURS).format(SQL_DATETIME);
        waitlistDao.markOffered(next.getId(), expires);

        ParkDTO park = parkDao.getById(parkId);
        String parkName = (park != null) ? park.getName() : ("park #" + parkId);
        notificationService.send(next.getVisitorId(), null, "SIM_EMAIL",
                "A slot opened for " + parkName + " on " + visitDate
                + " — you have " + GRAB_TTL_HOURS + " hour to claim it.");
    }

    /**
     * Sweeps every grab offer whose deadline has passed: the offeree forfeited, so
     * each lapsed entry is removed and the grab is advanced to the next eligible
     * waiting party for that same park/date (via {@link #offerGrabToNext}). The
     * forfeited reservation itself is left WAITING but with no queue entry — by
     * design this session only removes the entry; a later session decides the
     * forfeited reservation's fate.
     *
     * <p>Public and side-effecting but timer-free: a later Scheduler session drives
     * it on a fixed interval. This session exercises it manually.
     */
    public void expireOverdueOffers() {
        List<WaitlistEntryDTO> expired = waitlistDao.findExpiredOffers();
        for (WaitlistEntryDTO entry : expired) {
            waitlistDao.removeEntry(entry.getId());
            offerGrabToNext(entry.getParkId(), entry.getVisitDate());
        }
    }

    /**
     * Fans a reservation change out to every client subscribed to that
     * reservation id, via the shared {@link SubscriptionRegistry} (the same
     * realtime-push path the order domain uses). The subscription key's entity
     * string is always {@code "reservation"} so it matches what the client
     * screens subscribe to.
     *
     * @param event the change to broadcast; its {@code entityId} is the reservation id
     */
    private void publishReservation(ServerEvent event) {
        SubscriptionRegistry.getInstance().publish(
                new SubscriptionKey("reservation", event.getEntityId()), event);
    }
}
