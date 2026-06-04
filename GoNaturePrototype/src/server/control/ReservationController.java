package server.control;

import java.util.List;
import java.util.Set;

import common.dto.ClientRequest;
import common.dto.ReservationDTO;
import common.dto.RequestType;
import common.dto.ServerResponse;
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
 * {@link RequestType#LIST_RESERVATIONS}) are implemented; the remaining ops are
 * stubs pending later reservation-feature sessions.
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

            default:
                return new ServerResponse(false, "NOT_IMPLEMENTED: " + request.getType());
        }
    }
}
