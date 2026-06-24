package client.service;

import client.app.Session;
import client.net.ClientConnection;
import common.dto.ClientRequest;
import common.dto.ParamField;
import common.dto.RequestType;
import common.dto.ServerResponse;
import common.dto.VisitType;
import javafx.application.Platform;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Thin async wrapper around {@link ClientConnection}. Every call is dispatched
 * on a background thread and the resulting CompletableFuture is always
 * resolved on the JavaFX Application thread, so callers can update UI state
 * directly inside {@code thenAccept(...)}.
 *
 * Every completed call notifies any registered ConnectionListener so the
 * sidebar pill can reflect the current socket state.
 */
public class NetworkService {

    /** Notified after every completed call so the UI can reflect socket reachability. */
    @FunctionalInterface
    public interface ConnectionListener {
        /**
         * Invoked when the connection's reachability may have changed.
         *
         * @param reachable {@code true} if the socket is currently connected
         */
        void onConnectionChanged(boolean reachable);
    }

    /** The client session whose connection requests are sent over. */
    private final Session session;
    /** Registered connection-state listeners, notified after each call. */
    private final List<ConnectionListener> listeners = new ArrayList<>();

    /**
     * Creates a network service bound to a client session.
     *
     * @param session the session whose connection requests are sent over
     */
    public NetworkService(Session session) { this.session = session; }

    /**
     * Registers a listener notified of connection-state changes.
     *
     * @param l the listener to add
     */
    public void addConnectionListener(ConnectionListener l) { listeners.add(l); }

    /**
     * Probe a host:port pair by opening a fresh socket and sending a PING.
     * Returned future resolves with the opened ClientConnection on success
     * (caller is responsible for promoting it into the Session) or null on
     * failure with a message in the accompanying ServerResponse.
     *
     * @param host the host name or IP to probe
     * @param port the TCP port to probe
     * @return a future, completed on the JavaFX thread, with the probe outcome
     */
    public CompletableFuture<ProbeResult> probe(String host, int port) {
        CompletableFuture<ProbeResult> future = new CompletableFuture<>();
        Thread t = new Thread(() -> {
            ClientConnection conn = new ClientConnection(host, port);
            try {
                conn.connect();
                ServerResponse res = conn.sendRequest(new ClientRequest(RequestType.PING));
                if (res != null && res.isSuccess()) {
                    Platform.runLater(() -> future.complete(new ProbeResult(conn, res)));
                } else {
                    conn.close();
                    String msg = res == null ? "No response" : res.getMessage();
                    Platform.runLater(() -> future.complete(new ProbeResult(null,
                        new ServerResponse(false, msg))));
                }
            } catch (Exception ex) {
                conn.close();
                Platform.runLater(() -> future.complete(new ProbeResult(null,
                    new ServerResponse(false, ex.getMessage()))));
            }
        });
        t.setDaemon(true);
        t.start();
        return future;
    }

    /**
     * Send a request using the active Session connection, asynchronously.
     *
     * @param req the request to send
     * @return a future, completed on the JavaFX thread, with the server's response
     *         (an error {@link ServerResponse} if not connected or the send fails)
     */
    public CompletableFuture<ServerResponse> send(ClientRequest req) {
        CompletableFuture<ServerResponse> future = new CompletableFuture<>();
        Thread t = new Thread(() -> {
            ClientConnection conn = session.getConnection();
            ServerResponse res;
            if (conn == null) {
                res = new ServerResponse(false, "Not connected");
            } else {
                // sendRequest throws IOException on timeout/interrupt/drop
                // (correlation-future model). Catch any failure — including an
                // unexpected runtime error — and convert it to an error
                // ServerResponse, so the future ALWAYS completes with a result.
                // Otherwise an uncaught throwable here would leave the future
                // unresolved and the caller's thenAccept never runs, hanging the
                // UI on a disabled button rather than showing a clear message.
                try {
                    res = conn.sendRequest(req);
                } catch (Exception ex) {
                    String msg = ex.getMessage() == null ? ex.toString() : ex.getMessage();
                    res = new ServerResponse(false, msg);
                }
            }
            boolean reachable = conn != null && conn.isConnected();
            // res is reassigned in the if/else above, so the lambda needs its
            // own effectively-final reference to satisfy the capture rule.
            final ServerResponse finalRes = res;
            Platform.runLater(() -> {
                for (ConnectionListener l : listeners) l.onConnectionChanged(reachable);
                future.complete(finalRes);
            });
        });
        t.setDaemon(true);
        t.start();
        return future;
    }

    /**
     * Creates a reservation. {@code visitTime} may be {@code null} (no preferred
     * time). The {@link VisitType} is sent as the enum itself — it lives in
     * {@code common.dto} and is Serializable, so the server reads it back
     * directly without string parsing.
     *
     * @param parkId    target park id
     * @param visitorId national-ID-style visitor id
     * @param visitDate visit date, ISO {@code yyyy-MM-dd}
     * @param visitTime visit time {@code HH:mm:ss}, or {@code null}
     * @param partySize     number of people in the party
     * @param visitType     INDIVIDUAL, FAMILY, or GROUP
     * @param guideId       registered guide's id for GROUP visits, or {@code null} otherwise
     * @param paidInAdvance whether the visitor opts to pay up front (deepens the group discount)
     * @param email         the visitor's contact email (required; the booking's notification target)
     * @param phone         the visitor's contact phone (required)
     * @return future resolving (on the FX thread) with the server's response
     */
    public CompletableFuture<ServerResponse> createReservation(int parkId, long visitorId, String visitDate,
                                                               String visitTime, int partySize, VisitType visitType,
                                                               Long guideId, boolean paidInAdvance,
                                                               String email, String phone) {
        ClientRequest req = new ClientRequest(RequestType.CREATE_RESERVATION);
        req.put("parkId",        parkId);
        req.put("visitorId",     visitorId);
        req.put("visitDate",     visitDate);
        req.put("visitTime",     visitTime);   // nullable
        req.put("partySize",     partySize);
        req.put("visitType",     visitType);
        req.put("guideId",       guideId);     // null for non-group bookings
        req.put("paidInAdvance", paidInAdvance);
        req.put("email",         email);
        req.put("phone",         phone);
        return send(req);
    }

    /**
     * Lists every reservation owned by a visitor.
     *
     * @param visitorId the owning visitor's national id
     * @return a future, completed on the JavaFX thread, with the server's response
     */
    public CompletableFuture<ServerResponse> listReservations(long visitorId) {
        ClientRequest req = new ClientRequest(RequestType.LIST_RESERVATIONS);
        req.put("visitorId", visitorId);
        return send(req);
    }

    /**
     * Confirms a PENDING reservation (server enforces the legal transition).
     *
     * @param reservationId the reservation to confirm
     * @return a future, completed on the JavaFX thread, with the server's response
     */
    public CompletableFuture<ServerResponse> confirmReservation(int reservationId) {
        ClientRequest req = new ClientRequest(RequestType.CONFIRM_RESERVATION);
        req.put("reservationId", reservationId);
        return send(req);
    }

    /**
     * Cancels a PENDING/CONFIRMED/WAITING reservation (server enforces the legal
     * transition).
     *
     * @param reservationId the reservation to cancel
     * @return a future, completed on the JavaFX thread, with the server's response
     */
    public CompletableFuture<ServerResponse> cancelReservation(int reservationId) {
        ClientRequest req = new ClientRequest(RequestType.CANCEL_RESERVATION);
        req.put("reservationId", reservationId);
        return send(req);
    }

    /**
     * Reschedules a PENDING/CONFIRMED reservation (date / optional time / party
     * size). The server re-checks the status, the group cap and capacity, then
     * recomputes the price. On success the response {@code getData()} is a
     * {@link common.dto.ReservationUpdateResultDTO} carrying the updated
     * {@link common.dto.ReservationDTO} plus the old and new price so the caller can
     * settle the difference.
     *
     * @param reservationId the reservation to reschedule
     * @param visitDate     the new visit date, ISO {@code yyyy-MM-dd}
     * @param visitTime     the new visit time {@code HH:mm:ss}, or {@code null}
     * @param partySize     the new party size
     * @return a future, completed on the JavaFX thread, with the server's response
     */
    public CompletableFuture<ServerResponse> updateReservation(int reservationId, String visitDate,
                                                               String visitTime, int partySize) {
        ClientRequest req = new ClientRequest(RequestType.UPDATE_RESERVATION);
        req.put("reservationId", reservationId);
        req.put("visitDate",     visitDate);
        req.put("visitTime",     visitTime);   // nullable
        req.put("partySize",     partySize);
        return send(req);
    }

    /* ---------- Waiting list ---------------------------------------------- */

    /**
     * Joins the waiting list for a full park/date — same inputs as a booking. The
     * server creates a WAITING reservation (no capacity gate) plus its queue entry
     * and prices it server-side. Response {@code getData()} is the created
     * {@link common.dto.WaitlistEntryDTO}.
     *
     * @param parkId        target park id
     * @param visitorId     national-ID-style visitor id
     * @param visitDate     visit date, ISO {@code yyyy-MM-dd}
     * @param visitTime     visit time {@code HH:mm:ss}, or {@code null}
     * @param partySize     number of people in the party
     * @param visitType     INDIVIDUAL, FAMILY, or GROUP
     * @param guideId       registered guide's id for GROUP visits, or {@code null} otherwise
     * @param paidInAdvance whether the visitor opts to pay up front
     * @param email         the visitor's contact email (required; target for the grab-offer notification)
     * @param phone         the visitor's contact phone (required)
     * @return a future, completed on the JavaFX thread, with the server's response
     */
    public CompletableFuture<ServerResponse> joinWaitlist(int parkId, long visitorId, String visitDate,
                                                          String visitTime, int partySize, VisitType visitType,
                                                          Long guideId, boolean paidInAdvance,
                                                          String email, String phone) {
        ClientRequest req = new ClientRequest(RequestType.JOIN_WAITLIST);
        req.put("parkId",        parkId);
        req.put("visitorId",     visitorId);
        req.put("visitDate",     visitDate);
        req.put("visitTime",     visitTime);   // nullable
        req.put("partySize",     partySize);
        req.put("visitType",     visitType);
        req.put("guideId",       guideId);     // null for non-group
        req.put("paidInAdvance", paidInAdvance);
        req.put("email",         email);
        req.put("phone",         phone);
        return send(req);
    }

    /**
     * Lists a visitor's active waiting-list entries (queue position + any live grab
     * offer). Response {@code getData()} is a
     * {@code List<}{@link common.dto.WaitlistEntryDTO}{@code >}.
     *
     * @param visitorId the owning visitor's national id
     * @return a future, completed on the JavaFX thread, with the server's response
     */
    public CompletableFuture<ServerResponse> listWaitlist(long visitorId) {
        ClientRequest req = new ClientRequest(RequestType.LIST_WAITLIST);
        req.put("visitorId", visitorId);
        return send(req);
    }

    /**
     * Accepts an active grab offer, confirming the WAITING reservation (the server
     * re-validates the offer window and capacity). Response {@code getData()} is the
     * now-CONFIRMED {@link common.dto.ReservationDTO}.
     *
     * @param reservationId the offered WAITING reservation to claim
     * @return a future, completed on the JavaFX thread, with the server's response
     */
    public CompletableFuture<ServerResponse> acceptGrab(int reservationId) {
        ClientRequest req = new ClientRequest(RequestType.ACCEPT_GRAB);
        req.put("reservationId", reservationId);
        return send(req);
    }

    /**
     * Leaves the waiting list for a WAITING reservation. If a grab offer is active
     * this is a decline, and the server advances the freed slot to the next in line.
     *
     * @param reservationId the WAITING reservation to remove from the queue
     * @return a future, completed on the JavaFX thread, with the server's response
     */
    public CompletableFuture<ServerResponse> leaveWaitlist(int reservationId) {
        ClientRequest req = new ClientRequest(RequestType.LEAVE_WAITLIST);
        req.put("reservationId", reservationId);
        return send(req);
    }

    /* ---------- Parks & parameter-change approval workflow ---------------- */

    /**
     * Fetches the logged-in park manager's own park — no id is sent, so the
     * server derives it from the session. Response {@code getData()} is a
     * {@link common.dto.ParkDTO}.
     * @return a future, completed on the JavaFX thread, with the server's response
     */
    public CompletableFuture<ServerResponse> getMyPark() {
        return send(new ClientRequest(RequestType.GET_PARK));
    }

    /**
     * Lists every park, for the booking dropdown. Response {@code getData()} is a
     * {@code List<}{@link common.dto.ParkDTO}{@code >}.
     * @return a future, completed on the JavaFX thread, with the server's response
     */
    public CompletableFuture<ServerResponse> listParks() {
        return send(new ClientRequest(RequestType.LIST_PARKS));
    }

    /**
     * Submits a park-parameter change request (PARK_MANAGER only). Only the field
     * and new value travel on the wire — the target park is derived server-side
     * from the manager's own assignment, never trusted from the client. The change
     * is stored PENDING and does not touch the park until a department manager
     * approves it.
     *
     * @param field    which park parameter to change
     * @param newValue the requested new value
     * @return a future, completed on the JavaFX thread, with the server's response
     */
    public CompletableFuture<ServerResponse> requestParamChange(ParamField field, int newValue) {
        ClientRequest req = new ClientRequest(RequestType.REQUEST_PARAM_CHANGE);
        req.put("field",    field);
        req.put("newValue", newValue);
        return send(req);
    }

    /**
     * Lists all PENDING parameter-change requests (DEPT_MANAGER only). Response
     * {@code getData()} is a {@code List<}{@link common.dto.ParameterChangeRequestDTO}{@code >}.
     * @return a future, completed on the JavaFX thread, with the server's response
     */
    public CompletableFuture<ServerResponse> listPendingChanges() {
        return send(new ClientRequest(RequestType.LIST_PENDING_CHANGES));
    }

    /**
     * Approves a pending change (DEPT_MANAGER only); on success the new value is
     * written to the park. Fails if the request is no longer PENDING.
     *
     * @param requestId the change request to approve
     * @return a future, completed on the JavaFX thread, with the server's response
     */
    public CompletableFuture<ServerResponse> approveParamChange(int requestId) {
        ClientRequest req = new ClientRequest(RequestType.APPROVE_PARAM_CHANGE);
        req.put("requestId", requestId);
        return send(req);
    }

    /**
     * Rejects a pending change (DEPT_MANAGER only); the park is left unchanged.
     * Fails if the request is no longer PENDING.
     *
     * @param requestId the change request to reject
     * @return a future, completed on the JavaFX thread, with the server's response
     */
    public CompletableFuture<ServerResponse> rejectParamChange(int requestId) {
        ClientRequest req = new ClientRequest(RequestType.REJECT_PARAM_CHANGE);
        req.put("requestId", requestId);
        return send(req);
    }

    /* ---------- Promotions approval workflow ------------------------------ */

    /**
     * Defines a park promotion for approval (PARK_MANAGER only). Only the name,
     * percent and date window travel on the wire — the target park is derived
     * server-side from the manager's own assignment, never trusted from the client.
     * The promotion is stored PENDING and affects no price until a department
     * manager approves it.
     *
     * @param name            human-friendly promotion name
     * @param discountPercent the discount percentage off (0..100)
     * @param startDate       first active date (inclusive), ISO {@code yyyy-MM-dd}
     * @param endDate         last active date (inclusive), ISO {@code yyyy-MM-dd}
     * @return a future, completed on the JavaFX thread, with the server's response
     */
    public CompletableFuture<ServerResponse> createPromotion(String name, int discountPercent,
                                                             String startDate, String endDate) {
        ClientRequest req = new ClientRequest(RequestType.CREATE_PROMOTION);
        req.put("name",            name);
        req.put("discountPercent", discountPercent);
        req.put("startDate",       startDate);
        req.put("endDate",         endDate);
        return send(req);
    }

    /**
     * Lists the logged-in park manager's own promotions, all statuses (PARK_MANAGER
     * only — the server derives the park from the session). Response
     * {@code getData()} is a {@code List<}{@link common.dto.PromotionDTO}{@code >}.
     * @return a future, completed on the JavaFX thread, with the server's response
     */
    public CompletableFuture<ServerResponse> listPromotions() {
        return send(new ClientRequest(RequestType.LIST_PROMOTIONS));
    }

    /**
     * Lists all PENDING promotions across parks (DEPT_MANAGER only). Response
     * {@code getData()} is a {@code List<}{@link common.dto.PromotionDTO}{@code >}.
     * @return a future, completed on the JavaFX thread, with the server's response
     */
    public CompletableFuture<ServerResponse> listPendingPromotions() {
        return send(new ClientRequest(RequestType.LIST_PENDING_PROMOTIONS));
    }

    /**
     * Approves a pending promotion (DEPT_MANAGER only); once approved and active by
     * the visit date it discounts that park's visits. Fails if it is no longer PENDING.
     *
     * @param promotionId the promotion to approve
     * @return a future, completed on the JavaFX thread, with the server's response
     */
    public CompletableFuture<ServerResponse> approvePromotion(int promotionId) {
        ClientRequest req = new ClientRequest(RequestType.APPROVE_PROMOTION);
        req.put("promotionId", promotionId);
        return send(req);
    }

    /**
     * Rejects a pending promotion (DEPT_MANAGER only); no price is ever affected.
     * Fails if it is no longer PENDING.
     *
     * @param promotionId the promotion to reject
     * @return a future, completed on the JavaFX thread, with the server's response
     */
    public CompletableFuture<ServerResponse> rejectPromotion(int promotionId) {
        ClientRequest req = new ClientRequest(RequestType.REJECT_PROMOTION);
        req.put("promotionId", promotionId);
        return send(req);
    }

    /* ---------- Gate: entry / exit / casual walk-ins / occupancy ----------- */

    /**
     * Admits a visitor against a CONFIRMED reservation (PARK_EMPLOYEE only — the
     * server re-checks the role and that the gate operates the employee's own
     * park). The server validates the code, the matching visitor id, and the
     * park. Response {@code getData()} is the opened {@link common.dto.VisitDTO}.
     *
     * @param confirmationCode the booking confirmation code presented at the gate
     * @param visitorId        the national id presented, which must match the reservation
     * @return a future, completed on the JavaFX thread, with the server's response
     */
    public CompletableFuture<ServerResponse> enterVisit(int confirmationCode, long visitorId) {
        ClientRequest req = new ClientRequest(RequestType.ENTER_VISIT);
        req.put("confirmationCode", confirmationCode);
        req.put("visitorId",        visitorId);
        return send(req);
    }

    /**
     * Records a visitor's exit, closing their open visit (PARK_EMPLOYEE only). The
     * open visit is found by confirmation code when supplied, otherwise by visitor
     * id; a reservation-backed visit also flips its reservation to COMPLETED. Pass
     * exactly one of the two — the other as {@code null}.
     *
     * @param confirmationCode the booking confirmation code, or {@code null} to exit by visitor id
     * @param visitorId        the visitor's national id, or {@code null} to exit by code
     * @return a future, completed on the JavaFX thread, with the server's response
     */
    public CompletableFuture<ServerResponse> exitVisit(Integer confirmationCode, Long visitorId) {
        ClientRequest req = new ClientRequest(RequestType.EXIT_VISIT);
        if (confirmationCode != null) req.put("confirmationCode", confirmationCode);
        if (visitorId != null)        req.put("visitorId",        visitorId);
        return send(req);
    }

    /**
     * Records a casual walk-in's exit by its ticket number (PARK_EMPLOYEE only).
     * The ticket number is the visit id handed to the employee at admission — the
     * only handle for exiting an anonymous casual party (it has no confirmation
     * code or visitor id). The server closes the open visit at the employee's park.
     *
     * @param visitId the ticket number shown at casual admission
     * @return a future, completed on the JavaFX thread, with the server's response
     */
    public CompletableFuture<ServerResponse> exitVisitByTicket(int visitId) {
        ClientRequest req = new ClientRequest(RequestType.EXIT_VISIT);
        req.put("visitId", visitId);
        return send(req);
    }

    /**
     * Records a casual walk-in at the employee's park (PARK_EMPLOYEE only). The
     * server enforces the physical-capacity gate (rejecting with "park full" when
     * it would exceed {@code maxCapacity - gapSize}) and prices the visit; the
     * price is returned on the {@link common.dto.VisitDTO}, never recomputed here.
     *
     * @param partySize the number of people walking in
     * @param visitType INDIVIDUAL, FAMILY, or GROUP
     * @param visitorId an optional national id (a subscriber earns the member discount), or {@code null}
     * @return a future, completed on the JavaFX thread, with the server's response
     */
    public CompletableFuture<ServerResponse> casualVisit(int partySize, VisitType visitType, Long visitorId) {
        ClientRequest req = new ClientRequest(RequestType.CASUAL_VISIT);
        req.put("partySize", partySize);
        req.put("visitType", visitType);
        if (visitorId != null) req.put("visitorId", visitorId);
        return send(req);
    }

    /**
     * Fetches live occupancy for the employee's own park (PARK_EMPLOYEE only — no
     * park id is sent, so the server derives it from the session). Response
     * {@code getData()} is an {@link common.dto.OccupancyDTO}.
     * @return a future, completed on the JavaFX thread, with the server's response
     */
    public CompletableFuture<ServerResponse> currentOccupancy() {
        return send(new ClientRequest(RequestType.CURRENT_OCCUPANCY));
    }

    /**
     * Authenticates a staff user. On success the response carries a
     * {@link common.dto.UserDTO} in {@code getData()}; on failure the message
     * explains why ("Invalid username or password." / "already logged in
     * elsewhere.").
     *
     * @param username the staff member's username
     * @param password the staff member's password
     * @return a future, completed on the JavaFX thread, with the server's response
     */
    public CompletableFuture<ServerResponse> loginStaff(String username, String password) {
        ClientRequest req = new ClientRequest(RequestType.LOGIN_STAFF);
        req.put("username", username);
        req.put("password", password);
        return send(req);
    }

    /**
     * Authenticates a visitor by national ID and password. On success the response
     * carries a {@link common.dto.VisitorDTO} in {@code getData()}; on failure the
     * message explains why ("Invalid ID or password." / "already logged in
     * elsewhere.").
     *
     * @param visitorId the visitor's national id
     * @param password  the visitor's password
     * @return a future, completed on the JavaFX thread, with the server's response
     */
    public CompletableFuture<ServerResponse> loginVisitor(long visitorId, String password) {
        ClientRequest req = new ClientRequest(RequestType.LOGIN_VISITOR);
        req.put("visitorId", visitorId);
        req.put("password",  password);
        return send(req);
    }

    /**
     * Self-service signup: creates a regular (non-subscriber) visitor account with
     * the chosen password (no login required — it's reached from the sign-in
     * screen). On success the visitor can immediately sign in with their national
     * ID + password; on failure the message explains why (e.g. the id is already
     * registered).
     *
     * @param visitorId the visitor's national id
     * @param fullName  the visitor's full name
     * @param email     the visitor's email address
     * @param phone     the visitor's phone number
     * @param password  the password the visitor chose
     * @return a future, completed on the JavaFX thread, with the server's response
     */
    public CompletableFuture<ServerResponse> registerVisitor(long visitorId, String fullName,
                                                             String email, String phone, String password) {
        ClientRequest req = new ClientRequest(RequestType.REGISTER_VISITOR);
        req.put("visitorId", visitorId);
        req.put("fullName",  fullName);
        req.put("email",     email);
        req.put("phone",     phone);
        req.put("password",  password);
        return send(req);
    }

    /**
     * Updates the logged-in actor's own profile (the "My Profile" self-edit). No id
     * is sent — the server derives the actor from the session and edits only that
     * row. For a visitor pass name/email/phone; for a staff user pass the name and
     * {@code null} for email/phone (a staff email is display-only, not self-edited). On success
     * the response carries the refreshed {@link common.dto.VisitorDTO} /
     * {@link common.dto.UserDTO} so the caller can update the in-memory session.
     *
     * @param fullName the new full name (required)
     * @param email    the new email (visitor only; {@code null} for staff)
     * @param phone    the new phone (visitor only; {@code null} for staff)
     * @return a future, completed on the JavaFX thread, with the server's response
     */
    public CompletableFuture<ServerResponse> updateProfile(String fullName, String email, String phone) {
        ClientRequest req = new ClientRequest(RequestType.UPDATE_PROFILE);
        req.put("fullName", fullName);
        req.put("email",    email);   // null for staff
        req.put("phone",    phone);   // null for staff
        return send(req);
    }

    /**
     * Logs the current actor out, releasing the server-side single-login lock.
     *
     * @return a future, completed on the JavaFX thread, with the server's response
     */
    public CompletableFuture<ServerResponse> logout() {
        return send(new ClientRequest(RequestType.LOGOUT));
    }

    /* ---------- Notifications --------------------------------------------- */

    /**
     * Lists the logged-in actor's notifications, newest first (the offline-fetch
     * path for the notification center). The server derives the recipient from
     * the session, so no id is sent. Response {@code getData()} is a
     * {@code List<}{@link common.dto.NotificationDTO}{@code >}.
     * @return a future, completed on the JavaFX thread, with the server's response
     */
    public CompletableFuture<ServerResponse> listNotifications() {
        return send(new ClientRequest(RequestType.LIST_NOTIFICATIONS));
    }

    /**
     * Acknowledges (marks read) a single notification, clearing its unread state.
     *
     * @param notificationId the notification to acknowledge
     * @return a future, completed on the JavaFX thread, with the server's response
     */
    public CompletableFuture<ServerResponse> ackNotification(int notificationId) {
        ClientRequest req = new ClientRequest(RequestType.ACK_NOTIFICATION);
        req.put("notificationId", notificationId);
        return send(req);
    }

    /**
     * Registers a subscriber (SERVICE_REP only — the server re-checks the role).
     * The visitor is found-or-created from {@code visitorId} and the supplied
     * details, then promoted to a subscriber. On success the response carries a
     * {@link common.dto.VisitorDTO}; on failure the message explains why (e.g.
     * "already a subscriber" or "Only a service representative …").
     *
     * @param visitorId  the subscriber's national id
     * @param fullName   the subscriber's full name
     * @param phone      the subscriber's phone number
     * @param email      the subscriber's email address
     * @param familySize the subscriber's family size
     * @return a future, completed on the JavaFX thread, with the server's response
     */
    public CompletableFuture<ServerResponse> registerSubscriber(long visitorId, String fullName,
                                                                String phone, String email, int familySize) {
        ClientRequest req = new ClientRequest(RequestType.REGISTER_SUBSCRIBER);
        req.put("visitorId",  visitorId);
        req.put("fullName",   fullName);
        req.put("phone",      phone);
        req.put("email",      email);
        req.put("familySize", familySize);
        return send(req);
    }

    /**
     * Registers a visitor as a group guide (SERVICE_REP only — the server
     * re-checks the role and stamps the guide row with the logged-in rep's id).
     * The visitor is found-or-created from {@code visitorId} and the supplied
     * details without changing their subscriber status.
     *
     * @param visitorId the guide's national id
     * @param fullName  the guide's full name
     * @param phone     the guide's phone number
     * @param email     the guide's email address
     * @return a future, completed on the JavaFX thread, with the server's response
     */
    public CompletableFuture<ServerResponse> registerGuide(long visitorId, String fullName,
                                                           String phone, String email) {
        ClientRequest req = new ClientRequest(RequestType.REGISTER_GUIDE);
        req.put("visitorId", visitorId);
        req.put("fullName",  fullName);
        req.put("phone",     phone);
        req.put("email",     email);
        return send(req);
    }

    /* ---------- Reports --------------------------------------------------- */

    /**
     * Runs the Visits-by-Type report (DEPT_MANAGER only — the server re-checks the
     * role). Response {@code getData()} is a {@link common.dto.VisitsReportDTO}.
     *
     * @param from   inclusive range start, ISO {@code yyyy-MM-dd}
     * @param to     inclusive range end, ISO {@code yyyy-MM-dd}
     * @param parkId a specific park id, or {@code null} for the whole region (all parks)
     * @return a future, completed on the JavaFX thread, with the server's response
     */
    public CompletableFuture<ServerResponse> visitsReport(String from, String to, Integer parkId) {
        return reportRequest(RequestType.REPORT_VISITS_BY_TYPE, from, to, parkId);
    }

    /**
     * Runs the Cancellations report (DEPT_MANAGER only — the server re-checks the
     * role). Response {@code getData()} is a {@link common.dto.CancellationsReportDTO}.
     *
     * @param from   inclusive range start, ISO {@code yyyy-MM-dd}
     * @param to     inclusive range end, ISO {@code yyyy-MM-dd}
     * @param parkId a specific park id, or {@code null} for the whole region (all parks)
     * @return a future, completed on the JavaFX thread, with the server's response
     */
    public CompletableFuture<ServerResponse> cancellationsReport(String from, String to, Integer parkId) {
        return reportRequest(RequestType.REPORT_CANCELLATIONS, from, to, parkId);
    }

    /**
     * Runs the Usage report for the logged-in park manager's <em>own</em> park
     * (PARK_MANAGER only — the server re-checks the role and derives the park from
     * the session, so no park id is sent and there is no park filter). Response
     * {@code getData()} is a {@link common.dto.UsageReportDTO}.
     *
     * @param from inclusive range start, ISO {@code yyyy-MM-dd}
     * @param to   inclusive range end, ISO {@code yyyy-MM-dd}
     * @return a future, completed on the JavaFX thread, with the server's response
     */
    public CompletableFuture<ServerResponse> usageReport(String from, String to) {
        ClientRequest req = new ClientRequest(RequestType.REPORT_USAGE);
        req.put("from", from);
        req.put("to",   to);
        return send(req);
    }

    /**
     * Shared builder for the two report requests. A {@code null} park id travels as
     * the {@code "ALL"} sentinel, which the server normalises (together with a
     * missing/blank value) to "whole region".
     *
     * @param type   the report request type
     * @param from   inclusive range start, ISO {@code yyyy-MM-dd}
     * @param to     inclusive range end, ISO {@code yyyy-MM-dd}
     * @param parkId a specific park id, or {@code null} for the whole region
     * @return a future, completed on the JavaFX thread, with the server's response
     */
    private CompletableFuture<ServerResponse> reportRequest(RequestType type, String from, String to, Integer parkId) {
        ClientRequest req = new ClientRequest(type);
        req.put("from", from);
        req.put("to",   to);
        req.put("parkId", parkId == null ? "ALL" : parkId);
        return send(req);
    }

    /** Result bundle for {@link #probe(String, int)}. */
    public static final class ProbeResult {
        /** The opened connection on success, or {@code null} on failure. */
        public final ClientConnection connection;
        /** The PING response (or a synthetic error response on failure). */
        public final ServerResponse   response;
        /**
         * Bundles a probe's connection and response.
         *
         * @param connection the opened connection, or {@code null} on failure
         * @param response   the probe response
         */
        public ProbeResult(ClientConnection connection, ServerResponse response) {
            this.connection = connection;
            this.response   = response;
        }
        /** {@return {@code true} if the probe connected and the server replied success} */
        public boolean isSuccess() { return connection != null && response.isSuccess(); }
    }
}
