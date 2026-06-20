package common.dto;

import java.io.Serializable;

/**
 * A change notification pushed from the server to subscribed clients.
 *
 * <p>While a {@link ServerResponse} is the reply to a specific request a client
 * just made, a {@code ServerEvent} is an <em>unsolicited</em> message announcing
 * that some domain entity has changed on the server. Once the realtime push
 * channel is wired, the server emits one of these whenever an operation commits
 * (e.g. an order is created or updated by another client), and every client that
 * has subscribed to the affected entity receives it on its reader thread and
 * updates its UI without having to poll.
 *
 * <p>The class is immutable so events can be safely shared between threads on
 * both sides of the wire; clients should treat instances as read-only snapshots.
 */
public class ServerEvent implements Serializable {
/** Serialization identifier for this class. */
    private static final long serialVersionUID = 1L;
/** Stores the entity value used by this component. */

    private final String entity;
/** Stores the entity id value used by this component. */
    private final long entityId;
/** Stores the op value used by this component. */
    private final EventOp op;
/** Stores the payload value used by this component. */
    private final Serializable payload;
/** Stores the timestamp value used by this component. */
    private final long timestamp;

    /**
     * Creates an event stamped with the current wall-clock time.
     *
     * @param entity   logical entity name affected (for example {@code "Order"});
     *                 clients use this to route the event to interested screens
     * @param entityId primary-key identifier of the affected row
     * @param op       which lifecycle transition occurred
     * @param payload  serializable snapshot of the entity after the change, or
     *                 {@code null} for {@link EventOp#DELETED}
     */
    public ServerEvent(String entity, long entityId, EventOp op, Serializable payload) {
        this.entity = entity;
        this.entityId = entityId;
        this.op = op;
        this.payload = payload;
        this.timestamp = System.currentTimeMillis();
    }

    /**
     * Builds a {@link EventOp#CREATED CREATED} event for a freshly inserted entity.
     *
     * @param entity  logical entity name
     * @param id      identifier of the new row
     * @param payload snapshot of the new entity to ship to subscribers
     * @return a populated event ready to be broadcast
     */
    public static ServerEvent created(String entity, long id, Serializable payload) {
        return new ServerEvent(entity, id, EventOp.CREATED, payload);
    }

    /**
     * Builds an {@link EventOp#UPDATED UPDATED} event for a modified entity.
     *
     * @param entity  logical entity name
     * @param id      identifier of the modified row
     * @param payload snapshot of the entity after the change
     * @return a populated event ready to be broadcast
     */
    public static ServerEvent updated(String entity, long id, Serializable payload) {
        return new ServerEvent(entity, id, EventOp.UPDATED, payload);
    }

    /**
     * Builds a {@link EventOp#DELETED DELETED} event. No payload is carried because
     * the row no longer exists; clients react by removing or invalidating any cached
     * copy.
     *
     * @param entity logical entity name
     * @param id     identifier of the removed row
     * @return a populated event ready to be broadcast
     */
    public static ServerEvent deleted(String entity, long id) {
        return new ServerEvent(entity, id, EventOp.DELETED, null);
    }

    /** @return the logical entity name (e.g. {@code "Order"}) this event refers to */
    public String getEntity() {
        return entity;
    }

    /** @return the primary-key identifier of the affected row */
    public long getEntityId() {
        return entityId;
    }

    /** @return which lifecycle transition the server has just committed */
    public EventOp getOp() {
        return op;
    }

    /**
     * @return a serializable snapshot of the entity after the change, or
     *         {@code null} when {@link #getOp()} is {@link EventOp#DELETED}
     */
    public Serializable getPayload() {
        return payload;
    }

    /** @return server-side wall-clock time (epoch millis) when the event was constructed */
    public long getTimestamp() {
        return timestamp;
    }
}
