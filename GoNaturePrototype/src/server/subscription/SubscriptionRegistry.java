package server.subscription;

import common.dto.ServerEvent;
import common.dto.SubscriptionKey;
import server.net.ClientSession;

import java.io.IOException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * Process-wide registry mapping each {@link SubscriptionKey} to the set of
 * {@link ClientSession}s that want change events for it.
 *
 * <p>Lookups, mutations, and publishes are all thread-safe: the outer map is a
 * {@link ConcurrentHashMap} and each per-key bucket is a
 * {@link CopyOnWriteArraySet}, so the publish path can iterate a bucket without
 * locking while subscribers come and go on other threads.
 *
 * <p><strong>Publish-tolerates-broken-subscribers contract:</strong> when
 * {@link #publish} fans an event out, an {@link IOException} from any one
 * {@link ClientSession#sendEvent} is logged and swallowed; the loop continues
 * to the next subscriber. A single dead connection must never block
 * notifications to the rest of the subscriber set.
 */
public final class SubscriptionRegistry {

    /** The singleton instance. */
    private static final SubscriptionRegistry INSTANCE = new SubscriptionRegistry();

    /** Subscription key &rarr; the set of sessions subscribed to it. */
    private final ConcurrentHashMap<SubscriptionKey, Set<ClientSession>> map = new ConcurrentHashMap<>();

    /** Private: use {@link #getInstance()}. */
    private SubscriptionRegistry() {}

    /** {@return the singleton registry instance} */
    public static SubscriptionRegistry getInstance() {
        return INSTANCE;
    }

    /**
     * Records that {@code session} wants events for {@code key}, both in the
     * shared registry bucket and on the session itself (so disconnect cleanup
     * can find every key the session held).
     *
     * @param session the client session that issued the SUBSCRIBE
     * @param key     the resource being subscribed to
     */
    public void register(ClientSession session, SubscriptionKey key) {
        // computeIfAbsent is the only race-free way to lazily build the
        // per-key set; an if-then-put pair would let two concurrent registers
        // each create and store their own set, and one would be lost.
        map.computeIfAbsent(key, k -> new CopyOnWriteArraySet<>()).add(session);
        session.subscribe(key);
        System.out.println("[sub +] session=" + session.remoteAddressString() +
                           " entity=" + key.entity() + " id=" + key.entityId());
    }

    /**
     * Reverses a previous {@link #register}. Drops the per-key bucket if it
     * becomes empty so the map does not grow without bound under high churn.
     *
     * @param session the client session that issued the UNSUBSCRIBE
     * @param key     the resource being unsubscribed from
     */
    public void unregister(ClientSession session, SubscriptionKey key) {
        Set<ClientSession> bucket = map.get(key);
        if (bucket != null) {
            bucket.remove(session);
            // Use the two-arg remove so we only drop the bucket if it is the
            // same empty instance — protects against a register racing in
            // between our isEmpty check and the removal.
            if (bucket.isEmpty()) {
                map.remove(key, bucket);
            }
        }
        session.unsubscribe(key);
        System.out.println("[sub -] session=" + session.remoteAddressString() +
                           " entity=" + key.entity() + " id=" + key.entityId());
    }

    /**
     * Detaches {@code session} from every subscription it currently holds.
     * Called from the server's connection-cleanup path when a client
     * disconnects so the registry never tries to push to a closed socket.
     *
     * @param session the disconnecting client session
     */
    public void unregisterAll(ClientSession session) {
        for (SubscriptionKey key : session.subscriptions()) {
            unregister(session, key);
        }
    }

    /**
     * Fans {@code event} out to every session subscribed to {@code key}. Per
     * the publish-tolerates-broken-subscribers contract, an {@link IOException}
     * from any one subscriber is logged and ignored — the loop continues to the
     * next one so a single dead client cannot stall the channel.
     *
     * @param key   the resource the change happened to
     * @param event the event to push to each subscriber
     */
    public void publish(SubscriptionKey key, ServerEvent event) {
        Set<ClientSession> targets = map.getOrDefault(key, Set.of());
        for (ClientSession target : targets) {
            try {
                target.sendEvent(event);
            } catch (IOException e) {
                System.out.println("[push] drop session=" + target.remoteAddressString() +
                                   " (" + e.getMessage() + ")");
            }
        }
        System.out.println("[push] entity=" + key.entity() + " id=" + key.entityId() +
                           " op=" + event.getOp() + " -> " + targets.size() + " subscriber(s)");
    }
}
