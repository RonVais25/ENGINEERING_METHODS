package server.control;

import common.dto.NotificationDTO;
import common.dto.ServerEvent;
import common.dto.SubscriptionKey;
import server.dao.NotificationDAO;
import server.subscription.SubscriptionRegistry;

/**
 * Small stateless helper that other controllers call to send a notification to a
 * single recipient (a visitor or a staff user). Holds only its
 * {@link NotificationDAO} collaborator, so one instance is safely shared across
 * all client threads.
 *
 * <p><strong>Routing.</strong> {@link #send} persists the notification, marks it
 * delivered, then publishes a realtime {@link ServerEvent} on the same push
 * channel every other domain uses: the key is {@code ("notification", recipientId)}
 * and the recipient subscribes to it on login. If the recipient is online their
 * client receives the event and shows the simulation popup instantly; if they are
 * offline {@link SubscriptionRegistry#publish} is a no-op (no subscriber) and the
 * row simply waits in the table to be fetched on their next login via
 * {@code LIST_NOTIFICATIONS}. Either way the notification is persisted first, so
 * nothing is lost.
 *
 * <p>The published payload is a fully-resolved {@link NotificationDTO} (re-read
 * via {@link NotificationDAO#getById}) so it carries the recipient's contact in
 * {@link NotificationDTO#getSimulatedTarget()} — the client needs it to render the
 * <em>"would send via … to &lt;contact&gt;"</em> popup text without another round-trip.
 */
public class NotificationService {

    /** Creates the notification service. */
    public NotificationService() { }

    /** Stateless DAO collaborator, shared across all client threads. */
    private final NotificationDAO dao = new NotificationDAO();

    /**
     * Persists, delivers, and broadcasts one notification. Exactly one of
     * {@code visitorId}/{@code userId} should be non-null — that recipient's id is
     * also the publish key. A persistence failure is logged and swallowed (no
     * publish); a trigger must never fail its own operation because a courtesy
     * notification could not be stored.
     *
     * @param visitorId the recipient visitor's national id, or {@code null} for a staff recipient
     * @param userId    the recipient staff user's id, or {@code null} for a visitor recipient
     * @param channel   delivery channel ({@code SIM_EMAIL}/{@code SIM_SMS}/{@code POPUP})
     * @param body      the message body
     */
    public void send(Long visitorId, Integer userId, String channel, String body) {
        int id = dao.insert(visitorId, userId, channel, body);
        if (id < 0) {
            System.out.println("[notify] insert failed (visitor=" + visitorId +
                               " user=" + userId + ") — not published");
            return;
        }
        dao.markSent(id);

        NotificationDTO dto = dao.getById(id);
        if (dto == null) {
            System.out.println("[notify] re-read failed for id=" + id + " — not published");
            return;
        }

        long recipientId = (visitorId != null) ? visitorId : userId.longValue();
        SubscriptionRegistry.getInstance().publish(
                new SubscriptionKey("notification", recipientId),
                ServerEvent.created("notification", recipientId, dto));
    }
}
