package server.control;

import java.util.Set;

import common.dto.ClientRequest;
import common.dto.RequestType;
import common.dto.ServerResponse;
import common.dto.UserDTO;
import common.dto.VisitorDTO;
import server.dao.AuthDAO;
import server.net.ClientSession;

import static common.dto.RequestType.LOGIN_STAFF;
import static common.dto.RequestType.LOGIN_VISITOR;
import static common.dto.RequestType.LOGOUT;
import static common.dto.RequestType.REGISTER_GUIDE;
import static common.dto.RequestType.REGISTER_SUBSCRIBER;

/**
 * Owns the authentication / registration domain (login, logout, single-login
 * lock, subscriber &amp; guide registration).
 *
 * <p>Login takes a per-actor lock in {@code active_session} so the same staff
 * user or visitor cannot be logged in from two connections at once; the lock is
 * released on logout and, defensively, on disconnect (see
 * {@code OrderServer.handleClient}). Stateless and shared across all client
 * threads — only the final {@link AuthDAO} collaborator is held as state; the
 * per-connection login state lives on the {@link ClientSession}.
 *
 * <p>{@code REGISTER_SUBSCRIBER}/{@code REGISTER_GUIDE} remain stubbed pending
 * Phase 2.
 */
public class AuthController implements DomainController {

    private final AuthDAO dao = new AuthDAO();

    @Override
    public Set<RequestType> handledTypes() {
        return Set.of(LOGIN_STAFF, LOGIN_VISITOR, LOGOUT, REGISTER_SUBSCRIBER, REGISTER_GUIDE);
    }

    @Override
    public ServerResponse handle(ClientRequest request, ClientSession session) {

        switch (request.getType()) {

            case LOGIN_STAFF: {
                String username = (String) request.get("username");
                String password = (String) request.get("password");

                UserDTO u = dao.findStaffByCredentials(username, password);
                if (u == null) {
                    return new ServerResponse(false, "Invalid username or password.");
                }
                if (!dao.lock(u.getId(), "USER")) {
                    return new ServerResponse(false, "This user is already logged in elsewhere.");
                }
                session.setLoggedIn((long) u.getId(), "USER");
                return new ServerResponse(true, "Welcome " + u.getFullName(), u);
            }

            case LOGIN_VISITOR: {
                long visitorId = ((Number) request.get("visitorId")).longValue();

                VisitorDTO v = dao.findVisitorById(visitorId);
                if (v == null) {
                    return new ServerResponse(false, "Visitor ID not found.");
                }
                if (!dao.lock(v.getId(), "VISITOR")) {
                    return new ServerResponse(false, "This visitor is already logged in elsewhere.");
                }
                session.setLoggedIn(v.getId(), "VISITOR");
                return new ServerResponse(true, "Welcome " + v.getFullName(), v);
            }

            case LOGOUT: {
                Long actorId = session.getLoggedInActorId();
                if (actorId != null) {
                    dao.unlock(actorId, session.getLoggedInKind());
                    session.setLoggedIn(null, null);
                }
                return new ServerResponse(true, "Logged out.");
            }

            default:
                // REGISTER_SUBSCRIBER / REGISTER_GUIDE — Phase 2.
                return new ServerResponse(false, "NOT_IMPLEMENTED: " + request.getType());
        }
    }
}
