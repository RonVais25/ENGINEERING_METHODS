package server.control;

import java.util.Set;

import common.dto.ClientRequest;
import common.dto.OccupancyDTO;
import common.dto.ParkDTO;
import common.dto.ReservationDTO;
import common.dto.ReservationStatus;
import common.dto.RequestType;
import common.dto.Role;
import common.dto.ServerResponse;
import common.dto.UserDTO;
import common.dto.VisitDTO;
import common.dto.VisitType;
import common.dto.VisitorDTO;
import server.dao.AuthDAO;
import server.dao.MemberDAO;
import server.dao.ParkDAO;
import server.dao.ReservationDAO;
import server.dao.VisitDAO;
import server.net.ClientSession;

import static common.dto.RequestType.CASUAL_VISIT;
import static common.dto.RequestType.CURRENT_OCCUPANCY;
import static common.dto.RequestType.ENTER_VISIT;
import static common.dto.RequestType.EXIT_VISIT;
import static common.dto.RequestType.VISITOR_EXIT_VISIT;

/**
 * Owns the visit domain: park entry by reservation, exit, casual walk-ins, and
 * live occupancy. Stateless and shared across all client threads — only the
 * final DAO/service collaborators are held as fields (see {@link DomainController}).
 *
 * <p><strong>Trust boundary.</strong> Every op is gated to a {@code PARK_EMPLOYEE}
 * on the server (not merely in the UI) by recovering the logged-in actor from the
 * {@link ClientSession} via {@link AuthDAO#findUserById}, mirroring
 * {@link ParkController}. The gate's park is always the employee's own
 * {@code park_id} — never a client-supplied id — so a client cannot operate
 * another park's gate.
 *
 * <p><strong>Capacity model.</strong> Entry by reservation performs <em>no</em>
 * capacity check: the reservation already holds its slot. Casual walk-ins are
 * gated on <em>physical</em> occupancy (the sum of open-visit headcounts from
 * {@link VisitDAO#currentOccupancy}), rejecting when
 * {@code occupancy + party > maxCapacity - gapSize} — distinct from the
 * booking-time {@link ReservationDAO#availableCapacity}.
 */
public class VisitController implements DomainController {
/** Stores the visit dao value used by this component. */

    private final VisitDAO visitDao = new VisitDAO();
/** Stores the reservation dao value used by this component. */
    private final ReservationDAO reservationDao = new ReservationDAO();
/** Stores the park dao value used by this component. */
    private final ParkDAO parkDao = new ParkDAO();
/** Stores the auth dao value used by this component. */
    private final AuthDAO authDao = new AuthDAO();
    /** Member/subscriber lookup for family-visit rules. */
    private final MemberDAO memberDao = new MemberDAO();
    /** Stateless price calculator, shared across all client threads. */
    private final PricingService pricing = new PricingService();
/**
 * Performs the handled types operation.
 * @return the result produced by the operation
 */

    @Override
    public Set<RequestType> handledTypes() {
        return Set.of(ENTER_VISIT, EXIT_VISIT, VISITOR_EXIT_VISIT, CASUAL_VISIT, CURRENT_OCCUPANCY);
    }
/**
 * Handles the supplied request and returns the appropriate server response.
 * @param request value supplied to the operation
 * @param session value supplied to the operation
 * @return the result produced by the operation
 */

    @Override
    public ServerResponse handle(ClientRequest request, ClientSession session) {

        // Gate operations are park-employee only. The visitor self-exit operation
        // is handled separately because it is explicitly required to be available
        // to the booking owner as well.
        UserDTO me = null;
        int employeePark = -1;
        if (request.getType() != VISITOR_EXIT_VISIT) {
            me = currentEmployee(session);
            if (me == null) {
                return new ServerResponse(false, "Only a park employee can operate the gate.");
            }
            if (me.getParkId() == null) {
                return new ServerResponse(false, "You are not assigned to a park.");
            }
            employeePark = me.getParkId();
        }

        switch (request.getType()) {

            case ENTER_VISIT: {
                Object rawCode    = request.get("confirmationCode");
                Object rawVisitor = request.get("visitorId");
                if (rawCode == null || rawVisitor == null) {
                    return new ServerResponse(false, "A confirmation code and visitor id are required.");
                }
                int  confirmationCode = ((Number) rawCode).intValue();
                long visitorId        = ((Number) rawVisitor).longValue();

                ReservationDTO reservation = reservationDao.findByConfirmationCode(confirmationCode);
                if (reservation == null) {
                    return new ServerResponse(false, "No reservation matches that confirmation code.");
                }
                if (reservation.getStatus() != ReservationStatus.CONFIRMED) {
                    return new ServerResponse(false,
                            "That reservation is " + reservation.getStatus()
                            + "; only a CONFIRMED reservation can enter.");
                }
                if (reservation.getVisitorId() != visitorId) {
                    return new ServerResponse(false, "Visitor id does not match the reservation.");
                }
                if (reservation.getParkId() != employeePark) {
                    return new ServerResponse(false, "That reservation is for a different park.");
                }
                // Soft date check: the booking is for reservation.getVisitDate().
                // We intentionally do NOT reject a date mismatch here so seeded
                // test data (future-dated reservations) can still be admitted; a
                // real deployment would warn the operator.

                // No capacity check: the reservation already holds its slot. The
                // reservation is NOT completed yet — that happens on exit. Casual
                // pricing/visit_type stay NULL on a reservation visit (derive from
                // the reservation).
                int visitId = visitDao.insertVisit(reservation.getId(), employeePark, visitorId,
                        reservation.getPartySize(), null, null);
                if (visitId < 0) {
                    return new ServerResponse(false, "Could not record the entry.");
                }
                VisitDTO visit = visitDao.findById(visitId);
                return new ServerResponse(true, "Entry recorded.", visit);
            }

            case EXIT_VISIT: {
                // Exit by ticket number (casual walk-in), confirmation code, or
                // visitor id. The ticket path is the only way to exit an anonymous
                // casual party — it has no reservation/confirmation code and no
                // visitor id to look up by, so it is identified by its visit id.
                VisitDTO open;
                Object rawTicket  = request.get("visitId");
                Object rawCode    = request.get("confirmationCode");
                Object rawVisitor = request.get("visitorId");
                if (rawTicket != null) {
                    // findById returns a visit regardless of open/closed state, so
                    // the still-inside check below guards an already-used ticket.
                    open = visitDao.findById(((Number) rawTicket).intValue());
                    if (open != null && open.getExitedAt() != null) {
                        return new ServerResponse(false, "Ticket already used — that visit has already exited.");
                    }
                } else if (rawCode != null) {
                    open = visitDao.findOpenByConfirmation(((Number) rawCode).intValue());
                } else if (rawVisitor != null) {
                    open = visitDao.findOpenByVisitor(((Number) rawVisitor).longValue());
                } else {
                    return new ServerResponse(false, "A ticket number, confirmation code, or visitor id is required.");
                }
                if (open == null) {
                    return new ServerResponse(false, "No open visit found to exit.");
                }
                if (open.getParkId() != employeePark) {
                    return new ServerResponse(false, "That visit is at a different park.");
                }
                if (!visitDao.closeVisit(open.getId())) {
                    return new ServerResponse(false, "Could not close the visit.");
                }
                // A reservation visit completes its reservation on exit; a casual
                // visit has no reservation to complete.
                if (open.getReservationId() != null) {
                    reservationDao.updateStatus(open.getReservationId(), ReservationStatus.COMPLETED);
                }
                return new ServerResponse(true, "Exit recorded.");
            }

            case VISITOR_EXIT_VISIT: {
                Long actorId = session.getLoggedInActorId();
                if (actorId == null || !"VISITOR".equals(session.getLoggedInKind())) {
                    return new ServerResponse(false, "Only the booking visitor can record this exit.");
                }
                Object rawReservationId = request.get("reservationId");
                if (rawReservationId == null) {
                    return new ServerResponse(false, "A reservation id is required.");
                }
                int reservationId = ((Number) rawReservationId).intValue();
                ReservationDTO reservation = reservationDao.getById(reservationId);
                if (reservation == null) {
                    return new ServerResponse(false, "Reservation not found.");
                }
                if (reservation.getVisitorId() != actorId.longValue()) {
                    return new ServerResponse(false, "You can only exit your own reservation.");
                }
                if (reservation.getStatus() != ReservationStatus.CONFIRMED) {
                    return new ServerResponse(false,
                            "Only an active CONFIRMED reservation can be exited by the visitor.");
                }
                VisitDTO open = visitDao.findOpenByReservation(reservationId);
                if (open == null) {
                    return new ServerResponse(false, "No open visit found for this reservation.");
                }
                if (!visitDao.closeVisit(open.getId())) {
                    return new ServerResponse(false, "Could not close the visit.");
                }
                reservationDao.updateStatus(reservationId, ReservationStatus.COMPLETED);
                return new ServerResponse(true, "Visitor exit recorded for the whole reservation group.");
            }

            case CASUAL_VISIT: {
                Object rawParty = request.get("partySize");
                Object rawType  = request.get("visitType");
                if (rawParty == null || rawType == null) {
                    return new ServerResponse(false, "A party size and visit type are required.");
                }
                int partySize = ((Number) rawParty).intValue();
                if (partySize <= 0) {
                    return new ServerResponse(false, "Party size must be a positive number.");
                }
                VisitType visitType = (VisitType) rawType;

                // Optional visitor: anonymous walk-in if absent. A given visitor
                // who is a subscriber earns the member discount.
                Long visitorId = null;
                Object rawVisitor = request.get("visitorId");
                if (rawVisitor != null) {
                    visitorId = ((Number) rawVisitor).longValue();
                }

                ParkDTO park = parkDao.getById(employeePark);
                if (park == null) {
                    return new ServerResponse(false, "Your park could not be found.");
                }
                int current = visitDao.currentOccupancy(employeePark);
                if (current < 0) {
                    return new ServerResponse(false, "Could not determine current occupancy.");
                }
                // Physical capacity gate (NOT booking-time availability).
                int limit = park.getMaxCapacity() - park.getGapSize();
                if (current + partySize > limit) {
                    return new ServerResponse(false,
                            "Park full: " + current + " inside, " + (limit - current)
                            + " of " + limit + " free, party of " + partySize + ".");
                }

                boolean isMember = false;
                if (visitorId != null) {
                    VisitorDTO visitor = authDao.findVisitorById(visitorId);
                    isMember = visitor != null && visitor.isSubscriber();
                }
                boolean isGroup = (visitType == VisitType.GROUP);
                // Casual walk-in: preOrdered=false, prePaid=false (see guide-rule
                // note — a casual group's guide is charged under the current rule).
                if (visitType == VisitType.FAMILY) {
                    if (visitorId == null) {
                        return new ServerResponse(false, "Family casual visits require a subscriber visitor id.");
                    }
                    int familySize = memberDao.subscriberFamilySize(visitorId);
                    if (familySize <= 0) {
                        return new ServerResponse(false, "Family visits are available only for subscribers.");
                    }
                    if (partySize > familySize) {
                        return new ServerResponse(false,
                                "Party size exceeds the subscriber family size (" + familySize + ").");
                    }
                }
                int price = pricing.calculate(visitType, isGroup, partySize, false, false, isMember,
                        park.getSpecialDiscountPercent());

                int visitId = visitDao.insertVisit(null, employeePark, visitorId, partySize, visitType, price);
                if (visitId < 0) {
                    return new ServerResponse(false, "Could not record the casual visit.");
                }
                // The visit id doubles as the walk-in's ticket number: it is the
                // only handle for exiting an anonymous casual party later. It rides
                // back on the VisitDTO (getId), and is echoed in the message too.
                VisitDTO visit = visitDao.findById(visitId);
                return new ServerResponse(true,
                        "Casual visit recorded — ticket #" + visitId
                        + " (price: " + price + " cents).", visit);
            }

            case CURRENT_OCCUPANCY: {
                // A park id may be supplied; otherwise default to the employee's park.
                int targetPark = employeePark;
                Object rawParkId = request.get("parkId");
                if (rawParkId != null) {
                    targetPark = ((Number) rawParkId).intValue();
                }

                ParkDTO park = parkDao.getById(targetPark);
                if (park == null) {
                    return new ServerResponse(false, "Park not found.");
                }
                int current = visitDao.currentOccupancy(targetPark);
                if (current < 0) {
                    return new ServerResponse(false, "Could not determine current occupancy.");
                }
                int available = park.getMaxCapacity() - park.getGapSize() - current;
                OccupancyDTO occupancy = new OccupancyDTO(
                        targetPark, current, park.getMaxCapacity(), park.getGapSize(), available);
                return new ServerResponse(true, "Occupancy computed.", occupancy);
            }

            default:
                return new ServerResponse(false, "NOT_IMPLEMENTED: " + request.getType());
        }
    }

    /**
     * Recovers the park employee authenticated on this connection, for the
     * server-side role check shared by every visit op. Returns {@code null}
     * unless a {@code USER}-kind actor is logged in, still resolves to a
     * {@code user} row, and that user is a {@link Role#PARK_EMPLOYEE}.
     *
     * @param session the per-connection session
     * @return the logged-in park employee, or {@code null} if none/not an employee
     */
    private UserDTO currentEmployee(ClientSession session) {
        Long actorId = session.getLoggedInActorId();
        if (actorId == null || !"USER".equals(session.getLoggedInKind())) {
            return null;
        }
        UserDTO me = authDao.findUserById(actorId);
        if (me == null || me.getRole() != Role.PARK_EMPLOYEE) {
            return null;
        }
        return me;
    }
}
