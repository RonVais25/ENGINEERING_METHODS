/**
 * Real-time push registry: {@link server.subscription.SubscriptionRegistry} maps
 * subscription keys (e.g. a park's occupancy, a user's notifications) to the
 * connected client sessions interested in them, so the server can fan out
 * {@code ServerEvent}s only to the clients that care.
 */
package server.subscription;
