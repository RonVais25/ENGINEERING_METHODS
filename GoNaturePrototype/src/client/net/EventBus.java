package client.net;

import common.dto.ServerEvent;
import common.dto.SubscriptionKey;
import javafx.application.Platform;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Process-wide bus that dispatches {@link ServerEvent}s pushed from the server
 * to interested local subscribers on the JavaFX Application thread.
 *
 * <p>The reader thread in {@link ClientConnection} hands every inbound event
 * to {@link #dispatch(ServerEvent)}; the bus looks up the event's
 * {@link SubscriptionKey} (entity + id) in the subscriber map and fan-outs to
 * every registered callback via {@link Platform#runLater}, so handlers can
 * safely touch UI state.
 *
 * <p><strong>Broken-subscriber tolerance:</strong> an exception thrown by any
 * single callback is caught and logged; the loop continues to the next
 * subscriber. One screen's broken handler must never block notifications to
 * the rest.
 *
 * <p>The bus is a singleton so any controller anywhere in the client can call
 * {@link #getInstance()} without plumbing through {@code Session}.
 */
public final class EventBus {

    private static final EventBus INSTANCE = new EventBus();

    private final ConcurrentHashMap<SubscriptionKey, List<Consumer<ServerEvent>>> subscribers = new ConcurrentHashMap<>();

    private EventBus() {}

    /** @return the singleton event bus instance */
    public static EventBus getInstance() {
        return INSTANCE;
    }

    /**
     * Registers {@code cb} to be invoked on the FX thread whenever the server
     * pushes an event for {@code key}.
     *
     * @param key the resource the caller wants events for
     * @param cb  the callback to invoke per event
     * @return a {@link Subscription} handle whose
     *         {@link Subscription#unsubscribe()} detaches this callback again
     */
    public Subscription subscribe(SubscriptionKey key, Consumer<ServerEvent> cb) {
        // computeIfAbsent is race-free; a get-then-put pair would let two
        // concurrent subscribers each install their own list and lose one.
        subscribers.computeIfAbsent(key, k -> new CopyOnWriteArrayList<>()).add(cb);
        return new Subscription(key, cb);
    }

    /**
     * Looks up the subscriber list for the event's {@code (entity, entityId)}
     * pair and fan-outs to each callback via {@link Platform#runLater}.
     *
     * <p>The log line is emitted BEFORE the {@code runLater} call so the log
     * ordering matches the wire-receive ordering, not the FX-thread execution
     * ordering — useful when correlating server pushes with client behaviour.
     *
     * @param ev the event freshly received from the server
     */
    public void dispatch(ServerEvent ev) {
        SubscriptionKey key = new SubscriptionKey(ev.getEntity(), ev.getEntityId());
        System.out.println("[recv] entity=" + ev.getEntity() +
                           " id=" + ev.getEntityId() +
                           " op=" + ev.getOp());
        List<Consumer<ServerEvent>> list = subscribers.get(key);
        if (list == null) return;
        for (Consumer<ServerEvent> cb : list) {
            Platform.runLater(() -> {
                try {
                    cb.accept(ev);
                } catch (RuntimeException ex) {
                    System.out.println("[recv] subscriber threw " +
                                       ex.getClass().getSimpleName() +
                                       ": " + ex.getMessage());
                }
            });
        }
    }

    /**
     * Removes {@code cb} from the subscriber list for {@code key}. If the list
     * becomes empty, the map entry is dropped so the bus does not retain keys
     * for resources no screen is watching anymore. Called by
     * {@link Subscription#unsubscribe()} — not usually called directly.
     *
     * @param key the resource the callback was registered against
     * @param cb  the callback to detach
     */
    void removeSubscriber(SubscriptionKey key, Consumer<ServerEvent> cb) {
        List<Consumer<ServerEvent>> list = subscribers.get(key);
        if (list == null) return;
        list.remove(cb);
        if (list.isEmpty()) {
            // Two-arg remove so a concurrent re-subscribe under the same key
            // is not silently dropped by us collapsing the bucket.
            subscribers.remove(key, list);
        }
    }

    /**
     * Handle returned by {@link #subscribe} so callers can detach themselves
     * without holding references to the underlying callback or key.
     */
    public static final class Subscription {
        private final SubscriptionKey key;
        private final Consumer<ServerEvent> cb;

        private Subscription(SubscriptionKey key, Consumer<ServerEvent> cb) {
            this.key = key;
            this.cb = cb;
        }

        /**
         * @return the resource this subscription is registered against; used
         *         by controllers to issue a matching UNSUBSCRIBE request on
         *         the wire when they detach locally
         */
        public SubscriptionKey getKey() {
            return key;
        }

        /** Detaches the callback this subscription represents. Idempotent. */
        public void unsubscribe() {
            EventBus.getInstance().removeSubscriber(key, cb);
        }
    }
}
