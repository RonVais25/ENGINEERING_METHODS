package server.control;

import java.util.Set;

import common.dto.CancellationsReportDTO;
import common.dto.ClientRequest;
import common.dto.RequestType;
import common.dto.Role;
import common.dto.ServerResponse;
import common.dto.UserDTO;
import common.dto.VisitsReportDTO;
import server.dao.AuthDAO;
import server.dao.ReportDAO;
import server.net.ClientSession;

import static common.dto.RequestType.REPORT_CANCELLATIONS;
import static common.dto.RequestType.REPORT_VISITS_BY_TYPE;

/**
 * Owns the reporting domain: the Visits-by-Type and Cancellations reports the
 * department manager runs across the region. Stateless and shared across all
 * client threads — only the read-only DAO collaborators are held as state.
 *
 * <p><strong>Trust boundary.</strong> Both reports are department-manager only.
 * The role is enforced here on the server (not merely gated in the UI) by
 * recovering the logged-in actor's {@link Role} from the {@link ClientSession} via
 * {@link AuthDAO#findUserById}, exactly as {@link ParkController} guards its
 * department-manager operations.
 *
 * <p>Both ops read the same filter from the request: a required date range
 * ({@code from}, {@code to}, ISO {@code yyyy-MM-dd}) and an optional park
 * ({@code parkId}). A missing park id — or the sentinel string {@code "ALL"} — is
 * treated as the whole region.
 */
public class ReportController implements DomainController {

    /** Creates the report controller. */
    public ReportController() { }

    /** Report data access (visits-by-type, cancellations). */
    private final ReportDAO reportDao = new ReportDAO();
    /** Authentication data access (role checks). */
    private final AuthDAO authDao = new AuthDAO();
    
    /**
     * Returns the report-related request types handled by this controller.
     *
     * @return supported report request types
     */
    @Override
    public Set<RequestType> handledTypes() {
        return Set.of(REPORT_VISITS_BY_TYPE, REPORT_CANCELLATIONS);
    }
    
    /**
     * Processes report requests for a department manager.
     *
     * @param request client request containing report filters
     * @param session current client session
     * @return server response containing the requested report
     */
    @Override
    public ServerResponse handle(ClientRequest request, ClientSession session) {

        // Both reports are department-manager only; enforce server-side.
        UserDTO me = currentStaff(session);
        if (me == null || me.getRole() != Role.DEPT_MANAGER) {
            return new ServerResponse(false,
                    "Only a department manager can run reports.");
        }

        String from = (String) request.get("from");
        String to = (String) request.get("to");
        if (from == null || to == null) {
            return new ServerResponse(false, "A date range (from, to) is required.");
        }
        Integer parkId = parkIdParam(request);

        switch (request.getType()) {

            case REPORT_VISITS_BY_TYPE: {
                VisitsReportDTO report = reportDao.visitsByType(from, to, parkId);
                if (report == null) {
                    return new ServerResponse(false, "Could not build the visits report.");
                }
                return new ServerResponse(true, "Visits report ready.", report);
            }

            case REPORT_CANCELLATIONS: {
                CancellationsReportDTO report = reportDao.cancellations(from, to, parkId);
                if (report == null) {
                    return new ServerResponse(false, "Could not build the cancellations report.");
                }
                return new ServerResponse(true, "Cancellations report ready.", report);
            }

            default:
                return new ServerResponse(false, "NOT_IMPLEMENTED: " + request.getType());
        }
    }

    /**
     * Reads the optional {@code parkId} filter from the request, normalising
     * "no park" to {@code null}: a missing value, the sentinel string
     * {@code "ALL"} (any case), or a blank string all mean the whole region. A
     * numeric value (boxed {@link Number} or its string form) is returned as an
     * {@link Integer}.
     *
     * @param request the client request
     * @return the requested park id, or {@code null} for the whole region
     */
    private Integer parkIdParam(ClientRequest request) {
        Object raw = request.get("parkId");
        if (raw == null) {
            return null;
        }
        if (raw instanceof Number) {
            return ((Number) raw).intValue();
        }
        String s = raw.toString().trim();
        if (s.isEmpty() || "ALL".equalsIgnoreCase(s)) {
            return null;
        }
        try {
            return Integer.valueOf(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Recovers the staff user authenticated on this connection, for the
     * server-side role check. Returns {@code null} unless a {@code USER}-kind actor
     * is logged in and still resolves to a {@code user} row.
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
