package client.net;

import common.dto.ClientRequest;
import common.dto.ServerEvent;
import common.dto.ServerResponse;

import java.io.EOFException;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketException;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Persistent TCP connection to the GoNature server with async request/response
 * correlation and unsolicited-event support.
 *
 * <p>Lifecycle:
 * <ol>
 *   <li>construct {@code ClientConnection(host, port)}
 *   <li>{@link #connect()} — opens the socket, the object streams, and starts
 *       the reader thread
 *   <li>{@link #sendRequest(ClientRequest)} — many times, from any number of
 *       worker threads, multiplexed over the same socket
 *   <li>{@link #close()} — on application shutdown
 * </ol>
 *
 * <p><strong>Threading model.</strong> Exactly one reader thread reads
 * {@link #in}; the input stream is never synchronized because only one thread
 * touches it. Outbound writes go through {@code synchronized (out)} so multiple
 * worker threads can call {@code sendRequest} concurrently without garbling the
 * wire — every {@code writeObject + flush + reset} is atomic with respect to
 * any other write. The reader thread routes inbound {@link ServerResponse}s to
 * the originating caller via a per-request {@link CompletableFuture} keyed on
 * {@code correlationId}, and forwards {@link ServerEvent}s to
 * {@link EventBus#dispatch(ServerEvent)}.
 *
 * <p><strong>Must not be called from the JavaFX Application thread.</strong>
 * {@link #sendRequest(ClientRequest)} blocks for up to ten seconds on the
 * per-request future; if the FX thread invokes it the UI freezes. All sends
 * go through {@link client.service.NetworkService}, which already runs them
 * on a worker thread.
 */
public class ClientConnection {

    private static final int CONNECT_TIMEOUT_MS = 3000;
    private static final long REQUEST_TIMEOUT_SECONDS = 10;

    private final String host;
    private final int    port;

    // volatile so the reader thread, sender threads, and the close path all
    // see field updates without holding a common monitor.
    private volatile Socket socket;
    private volatile ObjectOutputStream out;
    private volatile ObjectInputStream  in;

    // Starts at 1 so id=0 stays the unset sentinel on the server side; any
    // inbound response with id=0 must be an orphan and indicates a bug.
    private final AtomicLong correlationCounter = new AtomicLong(1);
    private final ConcurrentHashMap<Long, CompletableFuture<ServerResponse>> pending = new ConcurrentHashMap<>();

    private volatile Thread readerThread;
    private volatile boolean shuttingDown = false;

    public ClientConnection(String host, int port) {
        this.host = host;
        this.port = port;
    }

    /**
     * Opens the socket and the object streams, then spawns the reader thread.
     * Throws if the connect handshake fails.
     *
     * @throws IOException if the TCP connect or stream-header exchange fails
     */
    public synchronized void connect() throws IOException {
        socket = new Socket();
        socket.connect(new InetSocketAddress(host, port), CONNECT_TIMEOUT_MS);
        // No SO_TIMEOUT — the reader thread blocks indefinitely on
        // readObject() and exits via shutdown (socket close), not via
        // repeated SocketTimeoutExceptions. Per-request timeouts are
        // handled by CompletableFuture.get(10s) in sendRequest.
        out = new ObjectOutputStream(socket.getOutputStream());
        out.flush();
        in  = new ObjectInputStream(socket.getInputStream());

        readerThread = new Thread(this::readLoop, "ClientConnection-reader");
        readerThread.setDaemon(true);
        readerThread.start();
    }

    /** @return {@code true} if the underlying socket is connected and not closed */
    public boolean isConnected() {
        Socket s = socket;
        return s != null && s.isConnected() && !s.isClosed();
    }

    /**
     * Sends a request and blocks the caller's thread until the matching
     * response arrives, the request times out, or the connection drops.
     *
     * <p>The caller's thread MUST NOT be the JavaFX Application thread; see
     * the class-level javadoc for why.
     *
     * @param req the request to send (its correlation id is assigned by this method)
     * @return the matching {@link ServerResponse}
     * @throws IOException on timeout, interrupt, send failure, or connection close
     */
    public ServerResponse sendRequest(ClientRequest req) throws IOException {
        ObjectOutputStream localOut = this.out;
        if (!isConnected() || localOut == null) {
            throw new IOException("Not connected");
        }

        long id = correlationCounter.getAndIncrement();
        req.setCorrelationId(id);
        CompletableFuture<ServerResponse> fut = new CompletableFuture<>();
        pending.put(id, fut);

        // On the success path the reader thread removes the pending entry
        // when it routes the response, so we must NOT remove it ourselves.
        // On every failure path we remove it here to keep the map bounded.
        boolean removePending = true;
        try {
            synchronized (localOut) {
                localOut.writeObject(req);
                localOut.flush();
                // Clear ObjectOutputStream's reference cache; without this a
                // re-sent DTO instance would ship as a back-reference and the
                // server-side stream would surface stale data.
                localOut.reset();
            }
            ServerResponse r = fut.get(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            removePending = false;
            return r;
        } catch (TimeoutException e) {
            throw new IOException("request id=" + id + " timed out");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("interrupted while awaiting response id=" + id);
        } catch (ExecutionException e) {
            // The reader's finally block completed our future exceptionally,
            // usually because the socket dropped. Surface the cause.
            throw new IOException("response future failed for id=" + id, e.getCause());
        } finally {
            if (removePending) pending.remove(id);
        }
    }

    private void readLoop() {
        ObjectInputStream localIn = this.in;
        try {
            while (!shuttingDown && !isClosed()) {
                Object obj;
                try {
                    obj = localIn.readObject();
                } catch (EOFException | SocketException eof) {
                    // Socket closed by either side — exit cleanly. The finally
                    // block completes every still-pending future so any
                    // sendRequest parked on fut.get() unblocks.
                    break;
                } catch (ClassNotFoundException cnf) {
                    // Server sent a class this client doesn't know — log and
                    // keep looping so one bad frame can't kill the channel.
                    System.out.println("[reader] unknown class on wire: " + cnf.getMessage());
                    continue;
                } catch (IOException ioe) {
                    if (shuttingDown || isClosed()) break;
                    System.out.println("[reader] I/O error: " + ioe.getMessage());
                    break;
                }

                if (obj instanceof ServerResponse r) {
                    CompletableFuture<ServerResponse> fut = pending.remove(r.getCorrelationId());
                    if (fut != null) {
                        fut.complete(r);
                    } else {
                        // A response arrived for a request whose future was
                        // never registered (or that already timed out and
                        // cleaned itself up). Either way the caller has moved
                        // on; just log and drop.
                        System.out.println("[orphan response id=" + r.getCorrelationId() + "]");
                    }
                } else if (obj instanceof ServerEvent ev) {
                    EventBus.getInstance().dispatch(ev);
                } else {
                    System.out.println("[unknown message type] " +
                                       (obj == null ? "null" : obj.getClass().getName()));
                }
            }
        } finally {
            // Unblock every thread parked on sendRequest's fut.get() — the
            // socket is gone and no response is coming.
            ConnectionClosedException reason = new ConnectionClosedException(
                shuttingDown ? "connection closed by client" : "connection lost");
            for (Map.Entry<Long, CompletableFuture<ServerResponse>> e : pending.entrySet()) {
                e.getValue().completeExceptionally(reason);
            }
            pending.clear();
            System.out.println("[reader] thread exiting (" +
                               (shuttingDown ? "shutdown" : "connection lost") + ")");
        }
    }

    private boolean isClosed() {
        Socket s = socket;
        return s == null || s.isClosed();
    }

    /**
     * Closes the connection and shuts the reader thread down cleanly. Pending
     * futures are completed exceptionally with {@link ConnectionClosedException}
     * inside the reader thread's finally block, so any thread parked on
     * {@link #sendRequest(ClientRequest)} unblocks immediately.
     */
    public synchronized void close() {
        shuttingDown = true;
        // Closing the socket unblocks readObject() with an EOFException or
        // SocketException, which lets the reader thread reach its finally and
        // drain the pending map. Stream references are intentionally NOT
        // nulled — sendRequest threads may still hold the old reference as
        // their synchronized() lock and a write on a closed stream surfaces
        // as a clean IOException rather than an NPE.
        try { if (socket != null) socket.close(); } catch (Exception ignored) {}
    }

    /**
     * Surfaces a connection drop to any thread waiting in
     * {@link #sendRequest(ClientRequest)} as a typed {@link IOException} so
     * callers can distinguish it from generic stream failures.
     */
    public static final class ConnectionClosedException extends IOException {
        private static final long serialVersionUID = 1L;
        public ConnectionClosedException(String message) { super(message); }
    }
}
