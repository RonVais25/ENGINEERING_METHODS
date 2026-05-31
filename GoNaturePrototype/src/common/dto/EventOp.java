package common.dto;

/**
 * Kind of change carried by a {@link ServerEvent} on the realtime push channel.
 *
 * <p>Each value names a lifecycle transition the server has just committed to
 * its state, and which subscribed clients should reflect in their UI. The enum
 * is implicitly {@link java.io.Serializable} (via {@link Enum}) so it can travel
 * inside a {@link ServerEvent} over the existing {@code ObjectOutputStream} wire
 * format. The enum has no instance fields, so no {@code transient} qualifier is
 * needed and nothing prevents serialization.
 */
public enum EventOp {
    /** A new entity instance was inserted on the server. */
    CREATED,
    /** An existing entity's fields were modified on the server. */
    UPDATED,
    /** An entity instance was removed on the server. */
    DELETED
}
