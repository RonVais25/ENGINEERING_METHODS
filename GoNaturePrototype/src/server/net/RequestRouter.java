package server.net;

import common.dto.ClientRequest;
import common.dto.OrderDTO;
import common.dto.ServerResponse;
import common.dto.SubscriptionKey;
import server.control.OrderController;
import server.subscription.SubscriptionRegistry;

public class RequestRouter {

    private OrderController controller = new OrderController();

    public ServerResponse handle(ClientRequest request, ClientSession session) {

        switch (request.getType()) {

            case PING:
                return new ServerResponse(true, "pong");

            case GET_ORDER:
                int orderNumber = (int) request.get("orderNumber");

                OrderDTO order = controller.getOrder(orderNumber);

                if (order != null) {
                    return new ServerResponse(true, "Order found.", order);
                }

                return new ServerResponse(false, "Order not found.");

            case UPDATE_ORDER:
                int updateOrderNumber = (int) request.get("orderNumber");
                String newDate = (String) request.get("newDate");
                int newVisitors = (int) request.get("newVisitors");

                boolean updated = controller.updateOrder(updateOrderNumber, newDate, newVisitors);

                if (updated) {
                    return new ServerResponse(true, "Order updated successfully.");
                }

                return new ServerResponse(false, "Update failed.");

            case INSERT_ORDER:
                String  insertDate     = (String) request.get("orderDate");
                int     insertVisitors = (int)    request.get("numberOfVisitors");
                int     subscriberId   = (int)    request.get("subscriberId");

                OrderDTO created = controller.insertOrder(insertDate, insertVisitors, subscriberId);

                if (created != null) {
                    return new ServerResponse(true,
                            "Booking created (#" + created.getOrderNumber() + ").", created);
                }

                return new ServerResponse(false, "Insert failed.");

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

            default:
                return new ServerResponse(false, "Unknown request.");
        }
    }
}
