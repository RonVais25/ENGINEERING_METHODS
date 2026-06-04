package server.control;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

import common.dto.ClientRequest;
import common.dto.ReservationDTO;
import common.dto.ReservationStatus;
import common.dto.RequestType;
import common.dto.ServerResponse;
import common.dto.VisitType;
import server.dao.ReservationDAO;
import server.net.ClientSession;

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
 * (INDIVIDUAL/FAMILY and guide-led GROUP), and the
 * {@link RequestType#CANCEL_RESERVATION}/{@link RequestType#CONFIRM_RESERVATION}
 * lifecycle ops. The waitlist ops (join/leave/accept-grab) remain stubs pending a
 * later session. Status transitions are gated by {@link #isLegalTransition} so the
 * rules live in one place and are enforced server-side regardless of the client UI.
 */
public class ReservationController implements DomainController {

    private final ReservationDAO dao = new ReservationDAO();

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
                        partySize * 5000,         // TODO: use PricingService when Payment lands
                        false,                    // paidInAdvance
                        confirmationCode,
                        null);                    // createdAt — DB default fills it

                int newId = dao.insert(toInsert);
                if (newId < 0) {
                    return new ServerResponse(false, "Could not create reservation.");
                }

                ReservationDTO created = dao.getById(newId);
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
                return new ServerResponse(true, "Reservation confirmed.", dao.getById(id));
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
                return new ServerResponse(true, "Reservation cancelled.", dao.getById(id));
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
}
