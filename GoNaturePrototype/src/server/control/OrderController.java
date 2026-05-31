package server.control;

import common.dto.OrderDTO;
import server.dao.OrderDAO;

/**
 * Handles order-related operations and communication
 * between the server and data access layer.
 */
public class OrderController {

    private OrderDAO dao = new OrderDAO();

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