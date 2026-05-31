package client.service;

import client.app.Session;
import client.net.ClientConnection;
import common.dto.ClientRequest;
import common.dto.RequestType;
import common.dto.ServerResponse;
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
