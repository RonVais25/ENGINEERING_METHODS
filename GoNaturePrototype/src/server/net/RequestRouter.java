package server.net;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import common.dto.ClientRequest;
import common.dto.RequestType;
import common.dto.ServerResponse;
import common.dto.SubscriptionKey;
import server.control.AuthController;
import server.control.DomainController;
import server.control.NotificationController;
import server.control.OrderController;
import server.control.ParkController;
import server.control.ReportController;
import server.control.ReservationController;
import server.control.VisitController;
import server.subscription.SubscriptionRegistry;

/**
 * Central request dispatcher. One instance is shared across all client threads
 * (constructed once by {@code OrderServer}).
 *
 * <p>Domain operations are delegated to per-domain {@link DomainController}s via
 * a {@link RequestType}-keyed map built at construction time. The infrastructure
 * ops PING / SUBSCRIBE / UNSUBSCRIBE are not a domain and stay handled inline.
 */
public class RequestRouter {

    private final Map<RequestType, DomainController> controllers = new HashMap<>();

    public RequestRouter() {
        // Every domain controller registered here. Each declares the ops it
        // owns via handledTypes(); we fan those out into the dispatch map.
        List<DomainController> registered = List.of(
                new OrderController(),
                new AuthController(),
                new ReservationController(),
                new ParkController(),
                new VisitController(),
                new ReportController(),
                new NotificationController());

        for (DomainController c : registered) {
            for (RequestType type : c.handledTypes()) {
                DomainController previous = controllers.put(type, c);
                // Fail fast: two controllers claiming the same op is a
                // copy-paste mistake we want to catch at startup, not in prod.
                if (previous != null) {
                    throw new IllegalStateException(
                            "Duplicate handler for " + type + ": "
                            + previous.getClass().getSimpleName() + " and "
                            + c.getClass().getSimpleName());
                }
            }
        }
    }

    public ServerResponse handle(ClientRequest request, ClientSession session) {

        RequestType type = request.getType();

        switch (type) {

            case PING:
                return new ServerResponse(true, "pong");

            case SUBSCRIBE: {
                String entity   = (String) request.get("entity");
                // Accept Integer or Long off the wire — the client may send
                // either depending on whether it boxed an int or a long.
                long   entityId = ((Number) request.get("entityId")).longValue();

                SubscriptionKey key = new SubscriptionKey(entity, entityId);
                SubscriptionRegistry.getInstance().register(session, key);

                ServerResponse resp = new ServerResponse(true, "subscribed");
                resp.setCorrelationId(request.getCorrelationId());
                return resp;
            }

            case UNSUBSCRIBE: {
                String entity   = (String) request.get("entity");
                long   entityId = ((Number) request.get("entityId")).longValue();

                SubscriptionKey key = new SubscriptionKey(entity, entityId);
                SubscriptionRegistry.getInstance().unregister(session, key);

                ServerResponse resp = new ServerResponse(true, "unsubscribed");
                resp.setCorrelationId(request.getCorrelationId());
                return resp;
            }

            default: {
                DomainController c = controllers.get(type);
                if (c != null) {
                    return c.handle(request, session);
                }
                return new ServerResponse(false, "Unknown request.");
            }
        }
    }
}
