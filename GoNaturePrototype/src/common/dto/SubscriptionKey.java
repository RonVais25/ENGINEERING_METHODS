package common.dto;

import java.io.Serializable;

/**
 * Identifies a single subscribable resource on the server — the pair
 * (entity name, numeric id) that a client wants change events for.
 *
 * <p>A {@code SUBSCRIBE} or {@code UNSUBSCRIBE} {@link ClientRequest} carries
 * these two values; the server stores the key in
 * {@code server.subscription.SubscriptionRegistry} and uses it to fan out a
 * matching {@link ServerEvent} to every subscribed client. The record lives in
 * {@code common.dto} because both sides of the wire need to construct and
 * compare instances.
 *
 * <p>Implements {@link Serializable} explicitly (records are not Serializable
 * by default) so it can travel over the existing {@link java.io.ObjectOutputStream}
 * inside a request payload.
 *
 * @param entity   logical entity name, e.g. {@code "Order"}
 * @param entityId numeric primary-key identifier of that entity
 */
public record SubscriptionKey(String entity, long entityId) implements Serializable {
    private static final long serialVersionUID = 1L;
}
