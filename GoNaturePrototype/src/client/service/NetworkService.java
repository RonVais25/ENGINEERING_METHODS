package client.service;

import client.app.Session;
import client.net.ClientConnection;
import common.dto.ClientRequest;
import common.dto.RequestType;
import common.dto.ServerResponse;
import common.dto.VisitType;
import javafx.application.Platform;

import java.io.IOException;
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

    @FunctionalInterface
    public interface ConnectionListener {
        void onConnectionChanged(boolean reachable);
    }

    private final Session session;
    private final List<ConnectionListener> listeners = new ArrayList<>();

    public NetworkService(Session session) { this.session = session; }

    public void addConnectionListener(ConnectionListener l) { listeners.add(l); }

    /**
     * Probe a host:port pair by opening a fresh socket and sending a PING.
     * Returned future resolves with the opened ClientConnection on success
     * (caller is responsible for promoting it into the Session) or null on
     * failure with a message in the accompanying ServerResponse.
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

    /** Send a request using the active Session connection, asynchronously. */
    public CompletableFuture<ServerResponse> send(ClientRequest req) {
        CompletableFuture<ServerResponse> future = new CompletableFuture<>();
        Thread t = new Thread(() -> {
            ClientConnection conn = session.getConnection();
            ServerResponse res;
            if (conn == null) {
                res = new ServerResponse(false, "Not connected");
            } else {
                // sendRequest now throws IOException on timeout/interrupt/drop
                // (correlation-future model). Convert to an error ServerResponse
                // so callers keep the existing "future completes with a result"
                // contract instead of propagating a checked exception.
                try {
                    res = conn.sendRequest(req);
                } catch (IOException ex) {
                    res = new ServerResponse(false, ex.getMessage());
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

    public CompletableFuture<ServerResponse> getOrder(int orderNumber) {
        ClientRequest req = new ClientRequest(RequestType.GET_ORDER);
        req.put("orderNumber", orderNumber);
        return send(req);
    }

    public CompletableFuture<ServerResponse> updateOrder(int orderNumber, String newDate, int newVisitors) {
        ClientRequest req = new ClientRequest(RequestType.UPDATE_ORDER);
        req.put("orderNumber", orderNumber);
        req.put("newDate",     newDate);
        req.put("newVisitors", newVisitors);
        return send(req);
    }

    public CompletableFuture<ServerResponse> insertOrder(String orderDate, int numberOfVisitors, int subscriberId) {
        ClientRequest req = new ClientRequest(RequestType.INSERT_ORDER);
        req.put("orderDate",        orderDate);
        req.put("numberOfVisitors", numberOfVisitors);
        req.put("subscriberId",     subscriberId);
        return send(req);
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
     * @param partySize number of people in the party
     * @param visitType INDIVIDUAL, FAMILY, or GROUP
     * @param guideId   registered guide's id for GROUP visits, or {@code null} otherwise
     * @return future resolving (on the FX thread) with the server's response
     */
    public CompletableFuture<ServerResponse> createReservation(int parkId, long visitorId, String visitDate,
                                                               String visitTime, int partySize, VisitType visitType,
                                                               Long guideId) {
        ClientRequest req = new ClientRequest(RequestType.CREATE_RESERVATION);
        req.put("parkId",    parkId);
        req.put("visitorId", visitorId);
        req.put("visitDate", visitDate);
        req.put("visitTime", visitTime);   // nullable
        req.put("partySize", partySize);
        req.put("visitType", visitType);
        req.put("guideId",   guideId);     // null for non-group bookings
        return send(req);
    }

    /** Lists every reservation owned by a visitor. */
    public CompletableFuture<ServerResponse> listReservations(long visitorId) {
        ClientRequest req = new ClientRequest(RequestType.LIST_RESERVATIONS);
        req.put("visitorId", visitorId);
        return send(req);
    }

    /** Confirms a PENDING reservation (server enforces the legal transition). */
    public CompletableFuture<ServerResponse> confirmReservation(int reservationId) {
        ClientRequest req = new ClientRequest(RequestType.CONFIRM_RESERVATION);
        req.put("reservationId", reservationId);
        return send(req);
    }

    /** Cancels a PENDING/CONFIRMED/WAITING reservation (server enforces the legal transition). */
    public CompletableFuture<ServerResponse> cancelReservation(int reservationId) {
        ClientRequest req = new ClientRequest(RequestType.CANCEL_RESERVATION);
        req.put("reservationId", reservationId);
        return send(req);
    }

    /**
     * Authenticates a staff user. On success the response carries a
     * {@link common.dto.UserDTO} in {@code getData()}; on failure the message
     * explains why ("Invalid username or password." / "already logged in
     * elsewhere.").
     */
    public CompletableFuture<ServerResponse> loginStaff(String username, String password) {
        ClientRequest req = new ClientRequest(RequestType.LOGIN_STAFF);
        req.put("username", username);
        req.put("password", password);
        return send(req);
    }

    /**
     * Authenticates a visitor by national ID. On success the response carries a
     * {@link common.dto.VisitorDTO} in {@code getData()}.
     */
    public CompletableFuture<ServerResponse> loginVisitor(long visitorId) {
        ClientRequest req = new ClientRequest(RequestType.LOGIN_VISITOR);
        req.put("visitorId", visitorId);
        return send(req);
    }

    /** Logs the current actor out, releasing the server-side single-login lock. */
    public CompletableFuture<ServerResponse> logout() {
        return send(new ClientRequest(RequestType.LOGOUT));
    }

    /** Result bundle for {@link #probe(String, int)}. */
    public static final class ProbeResult {
        public final ClientConnection connection;
        public final ServerResponse   response;
        public ProbeResult(ClientConnection connection, ServerResponse response) {
            this.connection = connection;
            this.response   = response;
        }
        public boolean isSuccess() { return connection != null && response.isSuccess(); }
    }
}
