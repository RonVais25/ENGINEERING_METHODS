package server.net;

import common.dto.ServerEvent;
import common.dto.ServerResponse;
import common.dto.SubscriptionKey;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Per-connection server-side state for one client.
 *
 * <p>Wraps the live socket and its two object streams, and tracks the set of
 * {@link SubscriptionKey}s the client is currently watching on the realtime
 * push channel.
 *
 * <p>All outbound traffic on this connection — both replies to client requests
 * ({@link #sendResponse(ServerResponse)}) and unsolicited pushes from the
 * subscription registry ({@link #sendEvent(ServerEvent)}) — funnels through the
 * single private {@code writeLock}. That shared lock is intentional: the
 * request-handling thread and the publisher thread share one
 * {@link ObjectOutputStream}, and interleaved {@code writeObject} calls would
 * produce garbled, undeserializable bytes on the wire. Sequencing them with the
 * same lock guarantees each frame ships atomically.
 */
public class ClientSession {
/** Stores the socket value used by this component. */

    private final Socket socket;
/** Stores the in value used by this component. */
    private final ObjectInputStream in;
/** Stores the out value used by this component. */
    private final ObjectOutputStream out;
/** Stores the subscriptions value used by this component. */
    private final Set<SubscriptionKey> subscriptions = new CopyOnWriteArraySet<>();
/** Stores the write lock value used by this component. */
    private final ReentrantLock writeLock = new ReentrantLock();

    // Who is authenticated on this connection, and which single-login lock kind
    // ("USER"/"VISITOR") was taken. Both null while logged out. Set on login,
    // cleared on logout; read on disconnect so OrderServer can release a stale
    // active_session lock left by a crashed/closed client.
/** Stores the logged in actor id value used by this component. */
    private Long loggedInActorId;
/** Stores the logged in kind value used by this component. */
    private String loggedInKind;

    /**
     * Wraps an already-connected socket and its already-constructed object streams.
     *
     * @param socket the connected client socket; owned by this session and closed by {@link #close()}
     * @param in     input stream for reading {@link common.dto.ClientRequest}s
     * @param out    output stream for writing responses and pushed events
     */
    public ClientSession(Socket socket, ObjectInputStream in, ObjectOutputStream out) {
        this.socket = socket;
        this.in = in;
        this.out = out;
    }

    /**
     * Sends a reply to a client request, serializing access to the output stream
     * so it never interleaves with a concurrent {@link #sendEvent}.
     *
     * @param resp the response to write
     * @throws IOException if the underlying stream fails — the caller (the
     *                     request-handling loop) drops the session on failure
     */
    public void sendResponse(ServerResponse resp) throws IOException {
        writeLock.lock();
        try {
            out.writeObject(resp);
            out.flush();
            // Clear ObjectOutputStream's reference cache so a subsequent write
            // of the same DTO instance ships fresh bytes; without reset() the
            // client would receive a stale back-reference for a mutated DTO.
            out.reset();
        } finally {
            writeLock.unlock();
        }
    }

    /**
     * Pushes an unsolicited event from the server to this subscribed client.
     * Uses the same {@code writeLock} as {@link #sendResponse} because both
     * write to the same {@link ObjectOutputStream}; interleaved writes would
     * produce garbled, undeserializable bytes.
     *
     * @param ev the event to push
     * @throws IOException if the underlying stream fails — the caller (the
     *                     subscription publish loop) catches this and continues
     *                     fan-out to the other subscribers
     */
    public void sendEvent(ServerEvent ev) throws IOException {
        writeLock.lock();
        try {
            out.writeObject(ev);
            out.flush();
            out.reset();
        } finally {
            writeLock.unlock();
        }
    }

    /**
     * Records that this client now wants change events for {@code key}.
     * Idempotent — re-subscribing the same key is a no-op.
     *
     * @param key the resource subscribed to
     */
    public void subscribe(SubscriptionKey key) {
        subscriptions.add(key);
    }

    /**
     * Removes a previous subscription. No-op if the key was not subscribed.
     *
     * @param key the resource to stop watching
     */
    public void unsubscribe(SubscriptionKey key) {
        subscriptions.remove(key);
    }

    /**
     * Read-only snapshot of every subscription this session currently holds.
     * Used by {@code SubscriptionRegistry.unregisterAll} to detach the session
     * from every per-key bucket on disconnect.
     *
     * @return an unmodifiable view of the live subscription set
     */
    public Set<SubscriptionKey> subscriptions() {
        return Collections.unmodifiableSet(subscriptions);
    }

    /**
     * @return the id of the actor authenticated on this connection, or
     *         {@code null} if no one is logged in
     */
    public Long getLoggedInActorId() {
        return loggedInActorId;
    }

    /**
     * @return the single-login lock kind held by this connection
     *         ({@code "USER"}/{@code "VISITOR"}), or {@code null} if logged out
     */
    public String getLoggedInKind() {
        return loggedInKind;
    }

    /**
     * Records (on login) or clears (on logout) who is authenticated on this
     * connection and which single-login lock kind they hold.
     *
     * @param actorId the authenticated actor's id, or {@code null} to clear on logout
     * @param kind    the lock kind {@code "USER"}/{@code "VISITOR"}, or {@code null} to clear
     */
    public void setLoggedIn(Long actorId, String kind) {
        this.loggedInActorId = actorId;
        this.loggedInKind = kind;
    }

    /**
     * Closes the streams and the socket. {@link IOException} is swallowed so a
     * partially-broken connection still tears down quietly. The actual
     * "client disconnected" log line is emitted by {@code OrderServer} as
     * {@code [conn -]} so it can carry the unsubscribe count alongside.
     */
    public void close() {
        try { in.close(); }     catch (IOException ignored) {}
        try { out.close(); }    catch (IOException ignored) {}
        try { socket.close(); } catch (IOException ignored) {}
    }

    /**
     * @return string form of the peer's socket address (or {@code "(unknown)"}
     *         if the address is no longer available) for use in log lines
     */
    public String remoteAddressString() {
        var addr = socket.getRemoteSocketAddress();
        return addr == null ? "(unknown)" : addr.toString();
    }
}
