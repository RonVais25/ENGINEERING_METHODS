package server.control;

import java.util.List;
import java.util.Set;

import common.dto.ChangeStatus;
import common.dto.ClientRequest;
import common.dto.ParamField;
import common.dto.ParameterChangeRequestDTO;
import common.dto.ParkDTO;
import common.dto.RequestType;
import common.dto.Role;
import common.dto.ServerResponse;
import common.dto.UserDTO;
import server.dao.AuthDAO;
import server.dao.ParameterChangeDAO;
import server.dao.ParkDAO;
import server.dao.ReservationDAO;
import server.net.ClientSession;

import static common.dto.RequestType.APPROVE_PARAM_CHANGE;
import static common.dto.RequestType.CHECK_AVAILABILITY;
import static common.dto.RequestType.GET_PARK;
import static common.dto.RequestType.LIST_PARKS;
import static common.dto.RequestType.LIST_PENDING_CHANGES;
import static common.dto.RequestType.REJECT_PARAM_CHANGE;
import static common.dto.RequestType.REQUEST_PARAM_CHANGE;

/**
 * Owns the park &amp; parameter-change domain: park lookup, the booking-dropdown
 * park list, capacity availability, and the parameter-change
 * request/approve/reject workflow. Stateless and shared across all client
 * threads — only the final DAO collaborators are held as state.
 *
 * <p><strong>Approval rule (core of this feature).</strong> A {@code PARK_MANAGER}
 * <em>requests</em> a change ({@link RequestType#REQUEST_PARAM_CHANGE}); it is
 * stored {@code PENDING} and does <strong>not</strong> touch the park. A
 * {@code DEPT_MANAGER} <em>approves</em> it ({@link RequestType#APPROVE_PARAM_CHANGE})
 * — only then is the new value written to the park row. Rejecting changes nothing.
 *
 * <p><strong>Trust boundary.</strong> Roles are enforced here on the server (not
 * merely gated in the UI) by recovering the logged-in actor's {@link Role} and
 * {@code park_id} from the {@link ClientSession} via {@link AuthDAO#findUserById}.
 * A manager's target park is always derived from their own {@code park_id} —
 * never from a client-supplied park id. Approve/reject re-check the {@code PENDING}
 * state so a decision is never applied twice.
 */
public class ParkController implements DomainController {
/** Stores the park dao value used by this component. */

    private final ParkDAO parkDao = new ParkDAO();
/** Stores the change dao value used by this component. */
    private final ParameterChangeDAO changeDao = new ParameterChangeDAO();
/** Stores the auth dao value used by this component. */
    private final AuthDAO authDao = new AuthDAO();
    /** Read-only collaborator for {@link RequestType#CHECK_AVAILABILITY}. */
    private final ReservationDAO reservationDao = new ReservationDAO();
    /** Stateless notification helper, shared across all client threads. */
    private final NotificationService notificationService = new NotificationService();
/**
 * Performs the handled types operation.
 * @return the result produced by the operation
 */

    @Override
    public Set<RequestType> handledTypes() {
        return Set.of(GET_PARK, LIST_PARKS, REQUEST_PARAM_CHANGE, LIST_PENDING_CHANGES,
                APPROVE_PARAM_CHANGE, REJECT_PARAM_CHANGE, CHECK_AVAILABILITY);
    }
/**
 * Handles the supplied request and returns the appropriate server response.
 * @param request value supplied to the operation
 * @param session value supplied to the operation
 * @return the result produced by the operation
 */

    @Override
    public ServerResponse handle(ClientRequest request, ClientSession session) {

        switch (request.getType()) {

            case GET_PARK: {
                if (session.getLoggedInActorId() == null) {
                    return new ServerResponse(false, "Not logged in.");
                }
                Object rawParkId = request.get("parkId");
                if (rawParkId != null) {
                    int parkId = ((Number) rawParkId).intValue();
                    ParkDTO park = parkDao.getById(parkId);
                    if (park == null) {
                        return new ServerResponse(false, "Park not found.");
                    }
                    return new ServerResponse(true, "Park found.", park);
                }
                // No parkId supplied -> a park manager asking for their own park.
                UserDTO me = currentStaff(session);
                if (me == null || me.getRole() != Role.PARK_MANAGER) {
                    return new ServerResponse(false,
                            "Only a park manager can fetch their own park without a park id.");
                }
                if (me.getParkId() == null) {
                    return new ServerResponse(false, "You are not assigned to a park.");
                }
                ParkDTO park = parkDao.getByManager(me.getId());
                if (park == null) {
                    return new ServerResponse(false, "Your park could not be found.");
                }
                return new ServerResponse(true, "Park found.", park);
            }

            case LIST_PARKS: {
                // Any logged-in actor (staff or visitor) may list parks for the
                // booking dropdown.
                if (session.getLoggedInActorId() == null) {
                    return new ServerResponse(false, "Not logged in.");
                }
                List<ParkDTO> parks = parkDao.listAll();
                return new ServerResponse(true, "Parks listed.", parks);
            }

            case REQUEST_PARAM_CHANGE: {
                UserDTO me = currentStaff(session);
                if (me == null || me.getRole() != Role.PARK_MANAGER) {
                    return new ServerResponse(false,
                            "Only a park manager can request a parameter change.");
                }
                if (me.getParkId() == null) {
                    return new ServerResponse(false, "You are not assigned to a park.");
                }
                ParamField field = (ParamField) request.get("field");
                if (field == null) {
                    return new ServerResponse(false, "A parameter field is required.");
                }
                Object rawNew = request.get("newValue");
                if (rawNew == null) {
                    return new ServerResponse(false, "A new value is required.");
                }
                int newValue = ((Number) rawNew).intValue();
                if (newValue < 0) {
                    return new ServerResponse(false, "Parameter value cannot be negative.");
                }
                if (field == ParamField.SPECIAL_DISCOUNT_PERCENT && newValue > 90) {
                    return new ServerResponse(false, "Special sale discount cannot exceed 90%.");
                }

                // Trust boundary: the target park is the manager's own, never a
                // client-supplied id. Read the current value to record as old_value.
                int parkId = me.getParkId();
                ParkDTO park = parkDao.getById(parkId);
                if (park == null) {
                    return new ServerResponse(false, "Your park could not be found.");
                }
                int oldValue = currentValue(park, field);

                int requestId = changeDao.insertRequest(parkId, me.getId(), field, oldValue, newValue);
                if (requestId < 0) {
                    return new ServerResponse(false, "Could not record the change request.");
                }
                return new ServerResponse(true,
                        "Change request submitted for approval (request #" + requestId + ").");
            }

            case LIST_PENDING_CHANGES: {
                UserDTO me = currentStaff(session);
                if (me == null || me.getRole() != Role.DEPT_MANAGER) {
                    return new ServerResponse(false,
                            "Only a department manager can list pending changes.");
                }
                List<ParameterChangeRequestDTO> pending = changeDao.listPending();
                return new ServerResponse(true, "Pending changes listed.", pending);
            }

            case APPROVE_PARAM_CHANGE: {
                UserDTO me = currentStaff(session);
                if (me == null || me.getRole() != Role.DEPT_MANAGER) {
                    return new ServerResponse(false,
                            "Only a department manager can approve a change.");
                }
                Object rawId = request.get("requestId");
                if (rawId == null) {
                    return new ServerResponse(false, "A request id is required.");
                }
                int requestId = ((Number) rawId).intValue();

                ParameterChangeRequestDTO req = changeDao.getById(requestId);
                if (req == null) {
                    return new ServerResponse(false, "Change request not found.");
                }
                if (req.getStatus() != ChangeStatus.PENDING) {
                    return new ServerResponse(false,
                            "That request is already " + req.getStatus() + ".");
                }
                // Flip to APPROVED under the atomic PENDING guard; only if that
                // wins do we apply the value to the park (no double-apply).
                if (!changeDao.decide(requestId, ChangeStatus.APPROVED, me.getId())) {
                    return new ServerResponse(false, "That request is no longer pending.");
                }
                parkDao.updateField(req.getParkId(), req.getField(), req.getNewValue());
                // Notify the requesting park manager their change was approved.
                notificationService.send(null, req.getRequestedBy(), "SIM_EMAIL",
                        "Your parameter change request #" + requestId + " was approved.");
                return new ServerResponse(true, "Change approved and applied.");
            }

            case REJECT_PARAM_CHANGE: {
                UserDTO me = currentStaff(session);
                if (me == null || me.getRole() != Role.DEPT_MANAGER) {
                    return new ServerResponse(false,
                            "Only a department manager can reject a change.");
                }
                Object rawId = request.get("requestId");
                if (rawId == null) {
                    return new ServerResponse(false, "A request id is required.");
                }
                int requestId = ((Number) rawId).intValue();

                ParameterChangeRequestDTO req = changeDao.getById(requestId);
                if (req == null) {
                    return new ServerResponse(false, "Change request not found.");
                }
                if (req.getStatus() != ChangeStatus.PENDING) {
                    return new ServerResponse(false,
                            "That request is already " + req.getStatus() + ".");
                }
                // Reject under the same PENDING guard; the park is left untouched.
                if (!changeDao.decide(requestId, ChangeStatus.REJECTED, me.getId())) {
                    return new ServerResponse(false, "That request is no longer pending.");
                }
                // Notify the requesting park manager their change was rejected.
                notificationService.send(null, req.getRequestedBy(), "SIM_EMAIL",
                        "Your parameter change request #" + requestId + " was rejected.");
                return new ServerResponse(true, "Change rejected.");
            }

            case CHECK_AVAILABILITY: {
                if (session.getLoggedInActorId() == null) {
                    return new ServerResponse(false, "Not logged in.");
                }
                Object rawParkId = request.get("parkId");
                String visitDate = (String) request.get("visitDate");
                if (rawParkId == null || visitDate == null) {
                    return new ServerResponse(false, "A park id and visit date are required.");
                }
                int parkId = ((Number) rawParkId).intValue();
                int free = reservationDao.availableCapacity(parkId, visitDate);
                if (free < 0) {
                    return new ServerResponse(false, "Could not determine availability.");
                }
                return new ServerResponse(true, "Availability computed.", free);
            }

            default:
                return new ServerResponse(false, "NOT_IMPLEMENTED: " + request.getType());
        }
    }

    /**
     * Recovers the staff user authenticated on this connection, for server-side
     * role checks. Returns {@code null} unless a {@code USER}-kind actor is logged
     * in and still resolves to a {@code user} row.
     *
     * @param session the per-connection session
     * @return the logged-in staff user, or {@code null} if none/not a staff login
     */
    private UserDTO currentStaff(ClientSession session) {
        Long actorId = session.getLoggedInActorId();
        if (actorId == null || !"USER".equals(session.getLoggedInKind())) {
            return null;
        }
        return authDao.findUserById(actorId);
    }

    /**
     * Reads a park's current value for the given parameter field — the
     * {@code old_value} recorded on a change request.
     *
     * @param park  the park to read from
     * @param field which parameter to read
     * @return the park's current value for that field
     */
    private int currentValue(ParkDTO park, ParamField field) {
        switch (field) {
            case MAX_CAPACITY:         return park.getMaxCapacity();
            case GAP_SIZE:             return park.getGapSize();
            case DEFAULT_STAY_MINUTES: return park.getDefaultStayMinutes();
            case SPECIAL_DISCOUNT_PERCENT: return park.getSpecialDiscountPercent();
            default:
                throw new IllegalArgumentException("Unknown park parameter field: " + field);
        }
    }
}
