package client.view;

import client.net.EventBus;
import client.service.NetworkService;
import common.dto.ClientRequest;
import common.dto.RequestType;
import common.dto.ServerEvent;
import common.dto.SubscriptionKey;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Lifecycle base for screens that participate in the realtime push channel.
 *
 * <p>Subclasses get four hooks:
 * <ul>
 *   <li>{@link #subscribe(String, long, Consumer)} — register interest in a
 *       server-side entity by id; sends a fire-and-forget SUBSCRIBE to the
 *       server and attaches a local {@link EventBus} callback. The
 *       {@link EventBus.Subscription} handle is retained so it can be detached
 *       automatically.</li>
 *   <li>{@link #onShow()} — non-final hook fired when the screen becomes
 *       active; default no-op. Subscribe here when the data the screen needs
 *       is known at activation time. Many screens subscribe later (after the
 *       user types an id), in which case they call {@link #subscribe} from
 *       their own callbacks instead.</li>
 *   <li>{@link #onHide()} — final; called by {@link client.app.Navigator}
 *       just before this screen is swapped out. Sends UNSUBSCRIBE for every
 *       active subscription, detaches the local callbacks, and then invokes
 *       {@link #onHideHook()}. Final on purpose so subclasses cannot forget
 *       the unsubscribe step.</li>
 *   <li>{@link #onHideHook()} — overridable template for additional cleanup
 *       (close auxiliary streams, cancel timers, etc.) without breaking the
 *       unsubscribe contract.</li>
 * </ul>
 *
 * <p><strong>Fire-and-forget SUBSCRIBE caveat.</strong> {@link #subscribe}
 * does not block on the server's reply. Between the SUBSCRIBE going out and
 * the registry acknowledging it, an event published by another client could
 * still slip past us. For a college-project demo this race is acceptable —
 * the worst case is missing the first event after a freshly opened screen.
 */
public abstract class BaseController {

    /** Shared NetworkService used for SUBSCRIBE / UNSUBSCRIBE wire traffic. */
    protected final NetworkService network;

    /** Active local subscriptions, drained on {@link #onHide()}. */
    private final List<EventBus.Subscription> activeSubs = new ArrayList<>();

    /**
     * Creates the controller with the shared network service.
     *
     * @param network the NetworkService instance shared across the client
     *                (constructed once in {@code GoNatureClientApp} and
     *                injected via the Navigator's controller factory)
     */
    protected BaseController(NetworkService network) {
        this.network = network;
    }

    /**
     * Registers interest in the entity identified by {@code (entity, entityId)}.
     * Sends a fire-and-forget SUBSCRIBE to the server and attaches the local
     * callback to the {@link EventBus}. The local subscription is tracked so
     * {@link #onHide()} can detach it automatically.
     *
     * @param entity   logical entity name, e.g. {@code "order"}
     * @param entityId numeric identifier of the entity to watch
     * @param cb       FX-thread callback invoked per incoming event
     */
    protected void subscribe(String entity, long entityId, Consumer<ServerEvent> cb) {
        ClientRequest req = new ClientRequest(RequestType.SUBSCRIBE);
        req.put("entity", entity);
        req.put("entityId", entityId);
        // Fire-and-forget — see class-level javadoc for the race caveat.
        network.send(req);

        SubscriptionKey key = new SubscriptionKey(entity, entityId);
        EventBus.Subscription sub = EventBus.getInstance().subscribe(key, cb);
        activeSubs.add(sub);
    }

    /**
     * Detaches every active subscription belonging to this controller and
     * sends matching UNSUBSCRIBE requests. Useful when a screen swaps the
     * subscribed entity mid-lifetime (e.g. user re-searches a different
     * order id) — {@link #onHide()} would only fire on navigation.
     */
    protected final void unsubscribeAll() {
        for (EventBus.Subscription sub : activeSubs) {
            SubscriptionKey key = sub.getKey();
            ClientRequest req = new ClientRequest(RequestType.UNSUBSCRIBE);
            req.put("entity",   key.entity());
            req.put("entityId", key.entityId());
            network.send(req);
            sub.unsubscribe();
        }
        activeSubs.clear();
    }

    /**
     * Called by {@link client.app.Navigator} after a fresh screen has been
     * loaded and its dependencies wired. Default implementation is a no-op;
     * subclasses override to subscribe when they know what to subscribe to
     * at activation time.
     */
    public void onShow() {
        // default no-op
    }

    /**
     * Called by {@link client.app.Navigator} just before this screen is
     * swapped out. Drains every active subscription, then invokes
     * {@link #onHideHook()} for subclass-specific cleanup. Final on purpose.
     */
    public final void onHide() {
        unsubscribeAll();
        onHideHook();
    }

    /**
     * Overridable template for additional cleanup beyond unsubscribing.
     * Default is a no-op.
     */
    protected void onHideHook() {
        // default no-op
    }
}
