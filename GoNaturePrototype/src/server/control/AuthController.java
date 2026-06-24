package server.control;

import java.util.Set;

import common.dto.ClientRequest;
import common.dto.RequestType;
import common.dto.Role;
import common.dto.ServerResponse;
import common.dto.UserDTO;
import common.dto.VisitorDTO;
import server.dao.AuthDAO;
import server.dao.MemberDAO;
import server.net.ClientSession;

import static common.dto.RequestType.LOGIN_STAFF;
import static common.dto.RequestType.LOGIN_VISITOR;
import static common.dto.RequestType.LOGOUT;
import static common.dto.RequestType.REGISTER_GUIDE;
import static common.dto.RequestType.REGISTER_SUBSCRIBER;
import static common.dto.RequestType.REGISTER_VISITOR;
import static common.dto.RequestType.UPDATE_PROFILE;

/**
 * Owns the authentication / registration domain (login, logout, single-login
 * lock, subscriber &amp; guide registration).
 *
 * <p>Login takes a per-actor lock in {@code active_session} so the same staff
 * user or visitor cannot be logged in from two connections at once; the lock is
 * released on logout and, defensively, on disconnect (see
 * {@code OrderServer.handleClient}). Stateless and shared across all client
 * threads — only the final {@link AuthDAO}/{@link MemberDAO} collaborators are
 * held as state; the per-connection login state lives on the
 * {@link ClientSession}.
 *
 * <p>{@code REGISTER_SUBSCRIBER}/{@code REGISTER_GUIDE} are SERVICE_REP-only.
 * The {@link ClientSession} carries only the logged-in actor's id, so the role
 * is re-read from the {@code user} row ({@link #isServiceRep}) and enforced
 * server-side — the screen being hidden on the client is convenience only.
 */
public class AuthController implements DomainController {

    /** Creates the authentication controller. */
    public AuthController() { }

    /**
     * Basic email shape check ({@code something@something.something}) — the same
     * shape the self-service signup uses. It is a sanity gate, not strict RFC
     * validation; the email column itself stays the authority on what is stored.
     */
    private static final String EMAIL_PATTERN = "[^@\\s]+@[^@\\s]+\\.[^@\\s]+";

    /**
     * Basic credit-card shape gate for subscriber registration: after stripping
     * spaces and dashes, the value must be 13-19 digits. This is a demo sanity
     * check, not a real validator (no Luhn, no processing); demo numbers like
     * {@code 4111-1111-1111-1111} pass. The same shape is enforced on the client,
     * but the server stays the authority since the client is never trusted.
     *
     * @param card the raw card text from the request (may be {@code null})
     * @return {@code true} if the card is present and digit-shaped
     */
    private static boolean isValidCreditCard(String card) {
        if (card == null) {
            return false;
        }
        return card.replaceAll("[\\s-]", "").matches("\\d{13,19}");
    }

    /** Authentication data access (login, single-login lock). */
    private final AuthDAO dao = new AuthDAO();
    /** Member/visitor data access (subscriber and guide registration). */
    private final MemberDAO memberDao = new MemberDAO();
    /**
     * Returns the request types handled by this controller.
     *
     * @return supported authentication request types
     */
    @Override
    public Set<RequestType> handledTypes() {
        return Set.of(LOGIN_STAFF, LOGIN_VISITOR, LOGOUT, REGISTER_VISITOR, REGISTER_SUBSCRIBER,
                      REGISTER_GUIDE, UPDATE_PROFILE);
    }
    /**
     * Handles authentication and registration requests.
     *
     * @param request client request to process
     * @param session current client session
     * @return server response for the requested operation
     */
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
                // Quick-login on the same connection: release whatever lock this
                // session already holds before taking the new one (see
                // releasePriorSessionLock), so the prior actor's lock cannot orphan.
                releasePriorSessionLock(session);
                if (!dao.lock(u.getId(), "USER")) {
                    return new ServerResponse(false, "This user is already logged in elsewhere.");
                }
                session.setLoggedIn((long) u.getId(), "USER");
                return new ServerResponse(true, "Welcome " + u.getFullName(), u);
            }

            case LOGIN_VISITOR: {
                long   visitorId = ((Number) request.get("visitorId")).longValue();
                String password  = (String) request.get("password");

                // Validate id + password server-side (never trust the client). A null
                // DTO covers both an unknown id and a wrong password, so reject both
                // with one non-revealing message.
                VisitorDTO v = dao.authenticateVisitor(visitorId, password);
                if (v == null) {
                    return new ServerResponse(false, "Invalid ID or password.");
                }
                // Quick-login on the same connection: release whatever lock this
                // session already holds before taking the new one (see
                // releasePriorSessionLock), so the prior actor's lock cannot orphan.
                releasePriorSessionLock(session);
                if (!dao.lock(v.getId(), "VISITOR")) {
                    return new ServerResponse(false, "This visitor is already logged in elsewhere.");
                }
                session.setLoggedIn(v.getId(), "VISITOR");
                return new ServerResponse(true, "Welcome " + v.getFullName(), v);
            }

            case REGISTER_VISITOR: {
                // Self-service signup: NO role gate — anyone on the login screen can
                // create a regular (non-subscriber) account. The server is the
                // authority on the stored password and on duplicate-id rejection.
                long   visitorId = ((Number) request.get("visitorId")).longValue();
                String fullName  = (String) request.get("fullName");
                String email     = (String) request.get("email");
                String phone     = (String) request.get("phone");
                String password  = (String) request.get("password");

                if (fullName == null || fullName.isBlank()
                        || password == null || password.isEmpty()) {
                    return new ServerResponse(false, "Full name and password are required.");
                }

                if (!dao.registerVisitor(visitorId, fullName.trim(), email, phone, password)) {
                    return new ServerResponse(false,
                            "An account with National ID " + visitorId
                            + " already exists. Please sign in instead.");
                }
                return new ServerResponse(true,
                        "Account created for " + fullName.trim() + ". You can now sign in.");
            }

            case LOGOUT: {
                Long actorId = session.getLoggedInActorId();
                if (actorId != null) {
                    dao.unlock(actorId, session.getLoggedInKind());
                    session.setLoggedIn(null, null);
                }
                return new ServerResponse(true, "Logged out.");
            }

            case REGISTER_SUBSCRIBER: {
                if (!isServiceRep(session)) {
                    return new ServerResponse(false,
                            "Only a service representative can register members/guides.");
                }

                long   visitorId  = ((Number) request.get("visitorId")).longValue();
                String fullName   = (String) request.get("fullName");
                String phone      = (String) request.get("phone");
                String email      = (String) request.get("email");
                int    familySize = ((Number) request.get("familySize")).intValue();
                String creditCard = (String) request.get("creditCard");

                // A (fake/demo) card is required on every subscriber: validate it is
                // present and digit-shaped server-side — the client is never trusted —
                // before the subscriber row that stores it is created. No real payment
                // processing happens; demo numbers like 4111-1111-1111-1111 are fine.
                if (!isValidCreditCard(creditCard)) {
                    return new ServerResponse(false,
                            "A valid credit card number (13-19 digits) is required.");
                }

                // Find-or-create the base visitor (marking them a subscriber), then
                // add the subscriber row — which stores the card whether this is a
                // brand-new subscriber or the upgrade of an existing visitor.
                // registerSubscriber returns false if they were already a subscriber.
                memberDao.upsertVisitor(visitorId, fullName, phone, email, true);
                if (!memberDao.registerSubscriber(visitorId, familySize, creditCard.trim())) {
                    return new ServerResponse(false,
                            "Visitor " + visitorId + " is already a subscriber.");
                }

                VisitorDTO v = new VisitorDTO(visitorId, fullName, phone, email, true);
                return new ServerResponse(true,
                        "Subscriber registered: " + fullName + " (#" + visitorId + ").", v);
            }

            case REGISTER_GUIDE: {
                if (!isServiceRep(session)) {
                    return new ServerResponse(false,
                            "Only a service representative can register members/guides.");
                }

                long   visitorId = ((Number) request.get("visitorId")).longValue();
                String fullName  = (String) request.get("fullName");
                String phone     = (String) request.get("phone");
                String email     = (String) request.get("email");

                // Find-or-create the base visitor without touching their subscriber
                // status, then add the guide row stamped with the logged-in rep's id.
                memberDao.upsertVisitor(visitorId, fullName, phone, email, false);
                int registeredBy = session.getLoggedInActorId().intValue();
                if (!memberDao.registerGuide(visitorId, registeredBy)) {
                    return new ServerResponse(false,
                            "Visitor " + visitorId + " is already a registered guide.");
                }

                return new ServerResponse(true,
                        "Guide registered: " + fullName + " (#" + visitorId + ").");
            }

            case UPDATE_PROFILE: {
                // Self-edit of personal details. Identity is taken ENTIRELY from the
                // logged-in session — any id in the request is ignored — so an actor
                // can only ever edit their OWN row. A visitor edits name/email/phone;
                // a staff user edits only their name (their email is display-only on
                // the profile screen). Never touched: national id / username, staff
                // email, role, park, is_subscriber, password.
                Long   actorId = session.getLoggedInActorId();
                String kind    = session.getLoggedInKind();
                if (actorId == null) {
                    return new ServerResponse(false, "You must be logged in to edit your profile.");
                }

                String fullName = (String) request.get("fullName");
                if (fullName == null || fullName.isBlank()) {
                    return new ServerResponse(false, "Full name is required.");
                }
                fullName = fullName.trim();

                if ("VISITOR".equals(kind)) {
                    String email = (String) request.get("email");
                    String phone = (String) request.get("phone");
                    if (email == null || !email.trim().matches(EMAIL_PATTERN)) {
                        return new ServerResponse(false, "Enter a valid email address.");
                    }
                    if (!dao.updateVisitorProfile(actorId, fullName, email.trim(),
                                                  phone == null ? null : phone.trim())) {
                        return new ServerResponse(false, "Could not update your profile. Please try again.");
                    }
                    // Return the freshly-read row so the client refreshes from the
                    // server's truth (and keeps is_subscriber, which it never edits).
                    VisitorDTO refreshed = dao.findVisitorById(actorId);
                    return new ServerResponse(true, "Your profile has been updated.", refreshed);
                }

                // Staff (USER): name only.
                if (!dao.updateStaffProfile(actorId.intValue(), fullName)) {
                    return new ServerResponse(false, "Could not update your profile. Please try again.");
                }
                UserDTO refreshed = dao.findUserById(actorId);
                return new ServerResponse(true, "Your profile has been updated.", refreshed);
            }

            default:
                return new ServerResponse(false, "Unsupported auth operation: " + request.getType());
        }
    }

    /**
     * Releases the single-login lock this connection currently holds, if any,
     * and clears its logged-in state — called at the start of a new login so a
     * quick-login that switches actors on the same socket (e.g. A then B then A)
     * does not orphan the prior actor's {@code active_session} row. An orphaned
     * lock would make re-logging that actor falsely report "already logged in
     * elsewhere".
     *
     * <p>It releases only the lock recorded on <em>this</em> session; it never
     * clears a lock merely because an incoming login names that actor. A genuine
     * second login of the same actor from a different connection therefore still
     * holds its own lock and is correctly rejected by {@link AuthDAO#lock}.
     *
     * @param session the per-connection session whose prior lock (if any) is released
     */
    private void releasePriorSessionLock(ClientSession session) {
        Long prevActor = session.getLoggedInActorId();
        if (prevActor != null) {
            dao.unlock(prevActor, session.getLoggedInKind());
            session.setLoggedIn(null, null);
        }
    }

    /**
     * Server-side authorization gate for member/guide registration: resolves the
     * connection's logged-in actor to a {@code SERVICE_REP}.
     *
     * <p>The {@link ClientSession} records only the actor id and lock kind, not
     * the role, so a visitor is rejected outright and a staff actor's role is
     * re-read from the {@code user} row via {@link AuthDAO#findUserById}. Gating
     * here (not just by hiding the client screen) is what actually enforces the
     * rule, since a crafted request could otherwise reach this controller.
     *
     * @param session the per-connection session of the caller
     * @return {@code true} if a staff user with the {@code SERVICE_REP} role is
     *         logged in on this connection, {@code false} otherwise
     */
    private boolean isServiceRep(ClientSession session) {
        Long actorId = session.getLoggedInActorId();
        if (actorId == null || !"USER".equals(session.getLoggedInKind())) {
            return false;
        }
        UserDTO u = dao.findUserById(actorId);
        return u != null && u.getRole() == Role.SERVICE_REP;
    }
}
