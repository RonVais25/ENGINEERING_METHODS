package server.control;

import java.util.List;
import java.util.Set;

import common.dto.ClientRequest;
import common.dto.NotificationDTO;
import common.dto.RequestType;
import common.dto.ServerResponse;
import server.dao.NotificationDAO;
import server.net.ClientSession;

import static common.dto.RequestType.ACK_NOTIFICATION;
import static common.dto.RequestType.LIST_NOTIFICATIONS;

/**
 * Owns the notification domain: listing the logged-in actor's messages and
 * acknowledging them. Stateless and shared across all client threads — only the
 * final {@link NotificationDAO} collaborator is held as state.
 *
 * <p>Both ops derive the recipient from the {@link ClientSession}, never from a
 * client-supplied id, so an actor can only ever read their own notifications:
 * {@link RequestType#LIST_NOTIFICATIONS} lists by the session's actor id and kind
 * ({@code VISITOR} → {@link NotificationDAO#listForVisitor}, otherwise
 * {@link NotificationDAO#listForUser}); {@link RequestType#ACK_NOTIFICATION} marks
 * one notification read.
 *
 * <p>Sending is not a request type — notifications are produced as a side effect
 * of other operations (reservation confirm/cancel, parameter-change decisions)
 * via {@link NotificationService}, which both persists the row and pushes it to
 * the online recipient.
 */
public class NotificationController implements DomainController {
/** Stores the dao value used by this component. */

    private final NotificationDAO dao = new NotificationDAO();
/**
 * Performs the handled types operation.
 * @return the result produced by the operation
 */

    @Override
    public Set<RequestType> handledTypes() {
        return Set.of(LIST_NOTIFICATIONS, ACK_NOTIFICATION);
    }
/**
 * Handles the supplied request and returns the appropriate server response.
 * @param request value supplied to the operation
 * @param session value supplied to the operation
 * @return the result produced by the operation
 */

    @Override
    public ServerResponse handle(ClientRequest request, ClientSession session) {

        switch (request.getType()) {

            case LIST_NOTIFICATIONS: {
                Long actorId = session.getLoggedInActorId();
                if (actorId == null) {
                    return new ServerResponse(false, "Not logged in.");
                }
                List<NotificationDTO> notifications;
                if ("VISITOR".equals(session.getLoggedInKind())) {
                    notifications = dao.listForVisitor(actorId);
                } else {
                    notifications = dao.listForUser(actorId.intValue());
                }
                return new ServerResponse(true, "Notifications listed.", notifications);
            }

            case ACK_NOTIFICATION: {
                if (session.getLoggedInActorId() == null) {
                    return new ServerResponse(false, "Not logged in.");
                }
                Object rawId = request.get("notificationId");
                if (rawId == null) {
                    return new ServerResponse(false, "A notification id is required.");
                }
                int notificationId = ((Number) rawId).intValue();
                if (!dao.ack(notificationId)) {
                    return new ServerResponse(false, "Acknowledge failed.");
                }
                return new ServerResponse(true, "Notification acknowledged.");
            }

            default:
                return new ServerResponse(false, "NOT_IMPLEMENTED: " + request.getType());
        }
    }
}
