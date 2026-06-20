package server.net;

import common.dto.ClientRequest;
import common.dto.ServerResponse;
import server.dao.AuthDAO;
import server.scheduler.SchedulerService;
import server.subscription.SubscriptionRegistry;

import java.io.EOFException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
/**
 * Represents the order server component of the GoNature application.
 */

public class OrderServer {
/** Stores the port value used by this component. */

    private final int port;
/** Stores the listener value used by this component. */
    private final ServerListener listener;
/** Stores the router value used by this component. */
    private final RequestRouter router = new RequestRouter();
/** Stores the server socket value used by this component. */

    private ServerSocket serverSocket;
/** Stores the running value used by this component. */
    private volatile boolean running;

    // Timed-job runner: started alongside the accept loop and shut down in stop()
    // so its threads live exactly as long as the server. Jobs log their one-line
    // summaries through the same ServerListener as the rest of the server.
/** Stores the scheduler value used by this component. */
    private final SchedulerService scheduler;

    // Every accepted client socket is tracked so stop() can force-close them.
    // Without this, server.stop() only stops accept() — existing handleClient
    // threads keep running and the clients can still send requests.
/** Stores the active clients value used by this component. */
    private final List<Socket> activeClients = Collections.synchronizedList(new ArrayList<>());
/**
 * Creates a new order server instance.
 * @param port value supplied to the operation
 * @param listener value supplied to the operation
 */

    public OrderServer(int port, ServerListener listener) {
        this.port = port;
        this.listener = listener;
        // Job summaries flow into the same activity log as connection events.
        this.scheduler = new SchedulerService(listener::onLog);
    }
/**
 * Performs the start operation.
 */

    public void start() {
        Thread t = new Thread(this::runAcceptLoop, "OrderServer-accept");
        t.setDaemon(true);
        t.start();
        // Begin the timed jobs once the accept loop is launching (DB password has
        // already been set by the GUI before start()).
        scheduler.start();
    }

    /**
     * The server's timed-job runner — exposed so the server console can wire its
     * manual "run now" triggers to {@link SchedulerService#runNow(String)} /
     * {@link SchedulerService#runAllNow()}.
     *
     * @return this server's scheduler
     */
    public SchedulerService getScheduler() {
        return scheduler;
    }
/**
 * Performs the stop operation.
 */

    public void stop() {
        running = false;
        // Stop the timed jobs first so no sweep runs against a tearing-down server.
        scheduler.shutdown();
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (Exception e) {
            listener.onError("Error closing server: " + e.getMessage());
        }

        // Force-close every still-open client socket so the handleClient
        // threads unwind and the clients see the connection drop immediately.
        synchronized (activeClients) {
            for (Socket s : new ArrayList<>(activeClients)) {
                try { s.close(); } catch (Exception ignored) {}
            }
            activeClients.clear();
        }
    }
/**
 * Performs the run accept loop operation.
 */

    private void runAcceptLoop() {
        try {
            serverSocket = new ServerSocket(port);
            running = true;
            listener.onStarted(port);

            while (running) {
                Socket clientSocket = serverSocket.accept();

                String ip   = clientSocket.getInetAddress().getHostAddress();
                String host = clientSocket.getInetAddress().getHostName();

                activeClients.add(clientSocket);
                listener.onClientConnected(ip, host);
                listener.onLog("[conn +] session=" + clientSocket.getRemoteSocketAddress());

                Thread t = new Thread(() -> handleClient(clientSocket, ip, host),
                                      "OrderServer-client-" + ip);
                t.setDaemon(true);
                t.start();
            }

        } catch (Exception e) {
            if (running) {
                listener.onError("Server error: " + e.getMessage());
            }
        } finally {
            running = false;
            listener.onStopped();
        }
    }
/**
 * Performs the handle client operation.
 * @param socket value supplied to the operation
 * @param ip value supplied to the operation
 * @param host value supplied to the operation
 */

    private void handleClient(Socket socket, String ip, String host) {
        // Persistent connection: read requests in a loop until the client
        // closes its socket (EOF) or the connection drops. The session ends
        // only on disconnect, not after each request.
        //
        // Outbound writes (responses + future pushed events) go through the
        // ClientSession so they share one writeLock; interleaved writes on the
        // same ObjectOutputStream would produce garbled bytes on the wire.
        ClientSession session = null;
        ObjectOutputStream out = null;
        ObjectInputStream  in  = null;
        try {
            out = new ObjectOutputStream(socket.getOutputStream());
            out.flush();
            in  = new ObjectInputStream(socket.getInputStream());
            session = new ClientSession(socket, in, out);

            while (!socket.isClosed()) {
                ClientRequest request;
                try {
                    request = (ClientRequest) in.readObject();
                } catch (EOFException | SocketException eof) {
                    // Client closed the socket — normal disconnect, not an error.
                    break;
                }

                listener.onLog("[req] session=" + session.remoteAddressString() +
                               " id=" + request.getCorrelationId() +
                               " type=" + request.getType());

                ServerResponse response = router.handle(request, session);
                // Echo the request's correlation id onto the response so the
                // client can match it back to the originating request via the
                // reader-thread routing introduced in step 3.
                response.setCorrelationId(request.getCorrelationId());
                session.sendResponse(response);

                listener.onLog("[resp] session=" + session.remoteAddressString() +
                               " id=" + request.getCorrelationId() +
                               " ok=" + response.isSuccess());
            }

        } catch (Exception e) {
            listener.onError("Client " + ip + " error: " + e.getMessage());
        } finally {
            if (session != null) {
                // Capture the peer address and subscription count BEFORE
                // unregisterAll drains the set, so the [conn -] log line
                // shows what we cleaned up. Detach from every subscription
                // bucket so the registry never tries to push to this dead
                // socket, then close streams+socket.
                String addr = session.remoteAddressString();
                int unsubCount = session.subscriptions().size();
                // Release any single-login lock held by this connection so a
                // crashed/closed client doesn't leave a stale active_session row
                // that blocks the actor from logging in again.
                if (session.getLoggedInActorId() != null) {
                    try {
                        new AuthDAO().unlock(session.getLoggedInActorId(), session.getLoggedInKind());
                    } catch (Exception ignored) {}
                }
                SubscriptionRegistry.getInstance().unregisterAll(session);
                session.close();
                listener.onLog("[conn -] session=" + addr +
                               " (unsubscribed " + unsubCount + " keys)");
            } else {
                // Session was never constructed (stream open failed). Close
                // whatever did get opened so we don't leak file descriptors.
                try { if (in  != null) in.close();  } catch (Exception ignored) {}
                try { if (out != null) out.close(); } catch (Exception ignored) {}
                try { socket.close(); } catch (Exception ignored) {}
            }
            activeClients.remove(socket);
            listener.onClientDisconnected(ip, host);
        }
    }
}
