package server.net;

import common.dto.ClientRequest;
import common.dto.OrderDTO;
import common.dto.ServerResponse;
import server.control.OrderController;

public class RequestRouter {

    private OrderController controller = new OrderController();

    public ServerResponse handle(ClientRequest request) {

        switch (request.getType()) {

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

            default:
                return new ServerResponse(false, "Unknown request.");
        }
    }
}