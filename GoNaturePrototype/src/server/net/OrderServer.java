package server.net;

import common.dto.ClientRequest;
import common.dto.ServerResponse;
import server.dao.AuthDAO;
import server.scheduler.SchedulerService;
import server.subscription.SubscriptionRegistry;
import server.util.ServerLog;

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
 * The TCP server: accepts client connections on a port, spawns a handler thread
 * per client that reads {@link ClientRequest}s in a loop and dispatches them
 * through a {@link RequestRouter}, and runs the {@link SchedulerService} timed
 * jobs for its lifetime. Lifecycle events and per-request activity are reported
 * through a {@link ServerListener}. Handler failures are contained per request so
 * one bad request never drops the whole connection.
 */
public class OrderServer {

    /** The TCP port to listen on. */
    private final int port;
    /** Receives lifecycle, connection, and activity events. */
    private final ServerListener listener;
    /** Shared request dispatcher, one instance across all client threads. */
    private final RequestRouter router = new RequestRouter();

    /** The listening server socket, or {@code null} while stopped. */
    private ServerSocket serverSocket;
    /** Whether the accept loop is running. */
    private volatile boolean running;

    // Timed-job runner: started alongside the accept loop and shut down in stop()
    // so its threads live exactly as long as the server. Jobs log their one-line
    // summaries through the same ServerListener as the rest of the server.
    /** The timed-job runner, started and stopped with the server. */
    private final SchedulerService scheduler;

    // Every accepted client socket is tracked so stop() can force-close them.
    // Without this, server.stop() only stops accept() — existing handleClient
    // threads keep running and the clients can still send requests.
    /** Every accepted client socket, so {@link #stop()} can force-close them. */
    private final List<Socket> activeClients = Collections.synchronizedList(new ArrayList<>());

    /**
     * Creates a server bound to a port and reporting to a listener.
     *
     * @param port     the TCP port to listen on
     * @param listener receives lifecycle, connection, and activity events
     */
    public OrderServer(int port, ServerListener listener) {
        this.port = port;
        this.listener = listener;
        // Job summaries flow into the same activity log as connection events.
        this.scheduler = new SchedulerService(listener::onLog);
    }

    /** Starts the accept loop (on a daemon thread) and the timed-job scheduler. */
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
     * Stops the scheduler, closes the listening socket, and force-closes every
     * still-open client connection so their handler threads unwind.
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

    /** Accept loop: binds the socket and spawns a handler thread per client. */
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
     * Per-client handler thread: reads requests in a loop until disconnect,
     * dispatching each through the router and writing the response, with per-request
     * failure containment and session/lock cleanup on teardown.
     *
     * @param socket the client socket
     * @param ip     the client's IP address
     * @param host   the client's resolved host name
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

                // Contain handler failures to THIS request. router.handle runs
                // arbitrary domain/DAO code; without this guard any exception it
                // throws would unwind handleClient and drop the whole connection.
                // We locate the failure in the server console (op + session +
                // stack), reply with a clear error, then fall through to send and
                // loop — so one bad request no longer kills the connection.
                ServerResponse response;
                try {
                    response = router.handle(request, session);
                    if (response == null) {
                        // A handler returning null would NPE on setCorrelationId
                        // below; treat it as a located server error too.
                        throw new IllegalStateException("handler returned no response");
                    }
                } catch (Throwable t) {
                    String op  = String.valueOf(request.getType());
                    String msg = (t.getMessage() == null) ? t.getClass().getSimpleName() : t.getMessage();
                    String ctx = "[ERROR] op=" + op +
                                 " session=" + session.remoteAddressString();
                    // Red line in the GUI activity log + full stack trace to the
                    // server console, so the failure is located, not silent.
                    listener.onError(ctx + ": " + msg);
                    ServerLog.error(ctx, t);
                    response = new ServerResponse(false,
                            "Server error processing " + op + ": " + msg);
                }

                // Echo the request's correlation id onto the response so the
                // client can match it back to the originating request via the
                // reader-thread routing introduced in step 3. A failure to write
                // here is a genuine connection drop — let it propagate to the
                // outer catch, which logs it and tears the session down.
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
