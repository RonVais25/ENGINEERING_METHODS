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
 * <p>The two read ops ({@link RequestType#GET_RESERVATION} and
 * {@link RequestType#LIST_RESERVATIONS}) plus {@link RequestType#CREATE_RESERVATION}
 * for INDIVIDUAL/FAMILY visits are implemented; the remaining ops (GROUP creation,
 * cancel, confirm, waitlist) are stubs pending later reservation-feature sessions.
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

                // This session handles INDIVIDUAL and FAMILY only — GROUP booking
                // (guide assignment, group pricing) arrives in a later session.
                if (visitType == VisitType.GROUP) {
                    return new ServerResponse(false, "Group booking not available yet.");
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
                        false,                    // isGroup — INDIVIDUAL/FAMILY only here
                        null,                     // guideId — none for non-group visits
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

            default:
                return new ServerResponse(false, "NOT_IMPLEMENTED: " + request.getType());
        }
    }
}
