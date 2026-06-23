package server.control;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import common.dto.ChangeStatus;
import common.dto.ClientRequest;
import common.dto.PromotionDTO;
import common.dto.RequestType;
import common.dto.Role;
import common.dto.ServerResponse;
import common.dto.UserDTO;
import server.dao.AuthDAO;
import server.dao.PromotionDAO;
import server.net.ClientSession;

import static common.dto.RequestType.APPROVE_PROMOTION;
import static common.dto.RequestType.CREATE_PROMOTION;
import static common.dto.RequestType.LIST_PENDING_PROMOTIONS;
import static common.dto.RequestType.LIST_PROMOTIONS;
import static common.dto.RequestType.REJECT_PROMOTION;

/**
 * Owns the promotion domain: a park manager <em>defines</em> a temporary discount
 * for their own park, a department manager <em>approves</em> or <em>rejects</em>
 * it, and an approved + active promotion grants an extra discount on that park's
 * visits (applied in {@link PricingService} at the booking/visit call sites).
 * Structurally a mirror of {@link ParkController}'s parameter-change workflow.
 *
 * <p><strong>Approval rule.</strong> A {@code PARK_MANAGER} creates a promotion
 * ({@link RequestType#CREATE_PROMOTION}); it is stored {@code PENDING} and does
 * <strong>not</strong> affect any price. A {@code DEPT_MANAGER} approves it
 * ({@link RequestType#APPROVE_PROMOTION}) — only an APPROVED promotion whose window
 * contains the visit date discounts a price. Rejecting changes nothing.
 *
 * <p><strong>Trust boundary.</strong> Exactly as {@link ParkController}, roles are
 * enforced here on the server (not merely gated in the UI) by recovering the
 * logged-in actor's {@link Role} and {@code park_id} from the {@link ClientSession}
 * via {@link AuthDAO#findUserById}. A manager's target park is always derived from
 * their own {@code park_id} — never from a client-supplied park id.
 *
 * <p>Stateless and shared across all client threads — only final, immutable
 * collaborators are held as fields (the {@link PromotionDAO} domain DAO, the
 * {@link AuthDAO} used purely for the role checks, and the stateless
 * {@link NotificationService}), exactly as {@link ParkController}.
 */
public class PromotionController implements DomainController {

    /** Creates the promotion controller. */
    public PromotionController() { }

    /** Promotion data access (create, list, decide, active-discount lookup). */
    private final PromotionDAO promotionDao = new PromotionDAO();
    /** Authentication data access (role checks), as in {@link ParkController}. */
    private final AuthDAO authDao = new AuthDAO();
    /** Stateless notification helper, shared across all client threads. */
    private final NotificationService notificationService = new NotificationService();

    /**
     * Returns the request types handled by this controller.
     *
     * @return supported promotion request types
     */
    @Override
    public Set<RequestType> handledTypes() {
        return Set.of(CREATE_PROMOTION, LIST_PROMOTIONS, LIST_PENDING_PROMOTIONS,
                APPROVE_PROMOTION, REJECT_PROMOTION);
    }

    /**
     * Processes promotion definition and approval requests.
     *
     * @param request client request to process
     * @param session current client session
     * @return server response containing the operation result
     */
    @Override
    public ServerResponse handle(ClientRequest request, ClientSession session) {

        switch (request.getType()) {

            case CREATE_PROMOTION: {
                UserDTO me = currentStaff(session);
                if (me == null || me.getRole() != Role.PARK_MANAGER) {
                    return new ServerResponse(false,
                            "Only a park manager can define a promotion.");
                }
                if (me.getParkId() == null) {
                    return new ServerResponse(false, "You are not assigned to a park.");
                }

                String name = (String) request.get("name");
                if (name == null || name.isBlank()) {
                    return new ServerResponse(false, "A promotion name is required.");
                }
                Object rawPercent = request.get("discountPercent");
                if (rawPercent == null) {
                    return new ServerResponse(false, "A discount percentage is required.");
                }
                int discountPercent = ((Number) rawPercent).intValue();
                if (discountPercent < 0 || discountPercent > 100) {
                    return new ServerResponse(false, "Discount percent must be between 0 and 100.");
                }
                String startDate = (String) request.get("startDate");
                String endDate = (String) request.get("endDate");
                if (startDate == null || endDate == null) {
                    return new ServerResponse(false, "A start date and end date are required.");
                }
                LocalDate start;
                LocalDate end;
                try {
                    start = LocalDate.parse(startDate);
                    end = LocalDate.parse(endDate);
                } catch (Exception ex) {
                    return new ServerResponse(false, "Dates must be valid (yyyy-MM-dd).");
                }
                if (end.isBefore(start)) {
                    return new ServerResponse(false, "The end date cannot be before the start date.");
                }

                // Trust boundary: the target park is the manager's own, never a
                // client-supplied id. Stored PENDING; affects no price until approved.
                int parkId = me.getParkId();
                PromotionDTO toInsert = new PromotionDTO(
                        0,                       // id assigned by the DB
                        parkId,
                        null,                    // parkName — display-only, not needed for insert
                        name.trim(),
                        discountPercent,
                        start.toString(),
                        end.toString(),
                        ChangeStatus.PENDING,
                        me.getId(),
                        null,                    // definedByName — display-only
                        null,                    // approvedBy — none yet
                        null);                   // createdAt — DB default fills it

                int promotionId = promotionDao.insert(toInsert);
                if (promotionId < 0) {
                    return new ServerResponse(false, "Could not record the promotion.");
                }
                return new ServerResponse(true,
                        "Promotion submitted for approval (promotion #" + promotionId + ").");
            }

            case LIST_PROMOTIONS: {
                UserDTO me = currentStaff(session);
                if (me == null || me.getRole() != Role.PARK_MANAGER) {
                    return new ServerResponse(false,
                            "Only a park manager can list their promotions.");
                }
                if (me.getParkId() == null) {
                    return new ServerResponse(false, "You are not assigned to a park.");
                }
                List<PromotionDTO> promotions = promotionDao.listByPark(me.getParkId());
                return new ServerResponse(true, "Promotions listed.", promotions);
            }

            case LIST_PENDING_PROMOTIONS: {
                UserDTO me = currentStaff(session);
                if (me == null || me.getRole() != Role.DEPT_MANAGER) {
                    return new ServerResponse(false,
                            "Only a department manager can list pending promotions.");
                }
                List<PromotionDTO> pending = promotionDao.listPending();
                return new ServerResponse(true, "Pending promotions listed.", pending);
            }

            case APPROVE_PROMOTION: {
                UserDTO me = currentStaff(session);
                if (me == null || me.getRole() != Role.DEPT_MANAGER) {
                    return new ServerResponse(false,
                            "Only a department manager can approve a promotion.");
                }
                Object rawId = request.get("promotionId");
                if (rawId == null) {
                    return new ServerResponse(false, "A promotion id is required.");
                }
                int promotionId = ((Number) rawId).intValue();

                PromotionDTO promo = promotionDao.getById(promotionId);
                if (promo == null) {
                    return new ServerResponse(false, "Promotion not found.");
                }
                if (promo.getStatus() != ChangeStatus.PENDING) {
                    return new ServerResponse(false,
                            "That promotion is already " + promo.getStatus() + ".");
                }
                // Flip to APPROVED under the atomic PENDING guard; only if that wins
                // do we notify (no double-notify on a racing decision).
                if (!promotionDao.approve(promotionId, me.getId())) {
                    return new ServerResponse(false, "That promotion is no longer pending.");
                }
                // Notify the defining park manager their promotion was approved.
                notificationService.send(null, promo.getDefinedBy(), "SIM_EMAIL",
                        "Your promotion \"" + promo.getName() + "\" (#" + promotionId + ") was approved.");
                return new ServerResponse(true, "Promotion approved.");
            }

            case REJECT_PROMOTION: {
                UserDTO me = currentStaff(session);
                if (me == null || me.getRole() != Role.DEPT_MANAGER) {
                    return new ServerResponse(false,
                            "Only a department manager can reject a promotion.");
                }
                Object rawId = request.get("promotionId");
                if (rawId == null) {
                    return new ServerResponse(false, "A promotion id is required.");
                }
                int promotionId = ((Number) rawId).intValue();

                PromotionDTO promo = promotionDao.getById(promotionId);
                if (promo == null) {
                    return new ServerResponse(false, "Promotion not found.");
                }
                if (promo.getStatus() != ChangeStatus.PENDING) {
                    return new ServerResponse(false,
                            "That promotion is already " + promo.getStatus() + ".");
                }
                // Reject under the same PENDING guard; affects no price either way.
                if (!promotionDao.reject(promotionId, me.getId())) {
                    return new ServerResponse(false, "That promotion is no longer pending.");
                }
                // Notify the defining park manager their promotion was rejected.
                notificationService.send(null, promo.getDefinedBy(), "SIM_EMAIL",
                        "Your promotion \"" + promo.getName() + "\" (#" + promotionId + ") was rejected.");
                return new ServerResponse(true, "Promotion rejected.");
            }

            default:
                return new ServerResponse(false, "NOT_IMPLEMENTED: " + request.getType());
        }
    }

    /**
     * Recovers the staff user authenticated on this connection, for server-side
     * role checks. Returns {@code null} unless a {@code USER}-kind actor is logged
     * in and still resolves to a {@code user} row. Mirrors
     * {@link ParkController#currentStaff}.
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
}
