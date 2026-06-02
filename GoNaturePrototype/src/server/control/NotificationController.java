package server.control;

import java.util.Set;

import common.dto.ClientRequest;
import common.dto.RequestType;
import common.dto.ServerResponse;
import server.net.ClientSession;

import static common.dto.RequestType.ACK_NOTIFICATION;
import static common.dto.RequestType.LIST_NOTIFICATIONS;

/**
 * Owns the notification domain (listing and acknowledging messages addressed
 * to a visitor or user). All ops are stubs pending the notification feature
 * session.
 */
public class NotificationController implements DomainController {

    @Override
    public Set<RequestType> handledTypes() {
        return Set.of(LIST_NOTIFICATIONS, ACK_NOTIFICATION);
    }

    @Override
    public ServerResponse handle(ClientRequest request, ClientSession session) {
        return new ServerResponse(false, "NOT_IMPLEMENTED: " + request.getType());
    }
}
