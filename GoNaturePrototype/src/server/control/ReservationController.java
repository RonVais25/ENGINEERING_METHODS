package server.control;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

import common.dto.ClientRequest;
import common.dto.ReservationDTO;
import common.dto.ReservationStatus;
import common.dto.RequestType;
import common.dto.ServerEvent;
import common.dto.ServerResponse;
import common.dto.SubscriptionKey;
import common.dto.VisitType;
import common.dto.VisitorDTO;
import server.dao.AuthDAO;
import server.dao.ReservationDAO;
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
 * lifecycle ops, and {@link RequestType#UPDATE_RESERVATION} (reschedule). The
 * waitlist ops (join/leave/accept-grab) remain stubs pending a later session.
 * Status transitions are gated by {@link #isLegalTransition} so the rules live in
 * one place and are enforced server-side regardless of the client UI.
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
