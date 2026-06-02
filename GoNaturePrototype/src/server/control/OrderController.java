package server.control;

import java.util.Set;

import common.dto.ClientRequest;
import common.dto.OrderDTO;
import common.dto.RequestType;
import common.dto.ServerEvent;
import common.dto.ServerResponse;
import common.dto.SubscriptionKey;
import server.dao.OrderDAO;
import server.net.ClientSession;
import server.subscription.SubscriptionRegistry;

import static common.dto.RequestType.GET_ORDER;
import static common.dto.RequestType.INSERT_ORDER;
import static common.dto.RequestType.UPDATE_ORDER;

/**
 * Owns the order domain: lookup, update (with realtime push) and insert.
 * Stateless and shared across all client threads — only the final
 * {@link OrderDAO} collaborator is held as state.
 */
public class OrderController implements DomainController {

    private final OrderDAO dao = new OrderDAO();

    @Override
    public Set<RequestType> handledTypes() {
        return Set.of(GET_ORDER, UPDATE_ORDER, INSERT_ORDER);
    }

    @Override
    public ServerResponse handle(ClientRequest request, ClientSession session) {

        switch (request.getType()) {

            case GET_ORDER: {
                int orderNumber = (int) request.get("orderNumber");

                OrderDTO order = getOrder(orderNumber);

                if (order != null) {
                    return new ServerResponse(true, "Order found.", order);
                }

                return new ServerResponse(false, "Order not found.");
            }

            case UPDATE_ORDER: {
                int updateOrderNumber = (int) request.get("orderNumber");
                String newDate = (String) request.get("newDate");
                int newVisitors = (int) request.get("newVisitors");

                boolean updated = updateOrder(updateOrderNumber, newDate, newVisitors);

                if (updated) {
                    // Re-fetch the canonical row so subscribers receive
                    // exactly what was persisted, not the request's input —
                    // protects against any DB-level normalization or
                    // partial-update behaviour drifting from what the client
                    // asked for. Publish runs AFTER the DAO commit (JDBC
                    // auto-commit), so a rollback couldn't leave subscribers
                    // with a phantom update.
                    OrderDTO fresh = getOrder(updateOrderNumber);
                    if (fresh != null) {
                        ServerEvent ev = ServerEvent.updated("order", updateOrderNumber, fresh);
                        SubscriptionRegistry.getInstance().publish(
                                new SubscriptionKey("order", updateOrderNumber), ev);
                    }
                    return new ServerResponse(true, "Order updated successfully.");
                }

                return new ServerResponse(false, "Update failed.");
            }

            case INSERT_ORDER: {
                String  insertDate     = (String) request.get("orderDate");
                int     insertVisitors = (int)    request.get("numberOfVisitors");
                int     subscriberId   = (int)    request.get("subscriberId");

                OrderDTO created = insertOrder(insertDate, insertVisitors, subscriberId);

                if (created != null) {
                    return new ServerResponse(true,
                            "Booking created (#" + created.getOrderNumber() + ").", created);
                }

                return new ServerResponse(false, "Insert failed.");
            }

            default:
                return new ServerResponse(false, "Unknown request.");
        }
    }

    /**
     * Retrieves an order by its order number.
     *
     * @param orderNumber the order number
     * @return the matching order
     */
    public OrderDTO getOrder(int orderNumber) {
        return dao.getOrderByNumber(orderNumber);
    }

    /**
     * Updates an existing order.
     *
     * @param orderNumber the order number
     * @param newDate the updated order date
     * @param newVisitors the updated number of visitors
     * @return true if the update succeeded
     */
    public boolean updateOrder(int orderNumber, String newDate, int newVisitors) {
        return dao.updateOrder(orderNumber, newDate, newVisitors);
    }

    /**
     * Inserts a new order into the system.
     *
     * @param orderDate the order date
     * @param numberOfVisitors number of visitors in the order
     * @param subscriberId the subscriber ID
     * @return the created order
     */
    public OrderDTO insertOrder(String orderDate, int numberOfVisitors, int subscriberId) {
        return dao.insertOrder(orderDate, numberOfVisitors, subscriberId);
    }
}
