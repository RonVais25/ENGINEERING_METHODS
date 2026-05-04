package server.control;

import common.dto.OrderDTO;
import server.dao.OrderDAO;

public class OrderController {

    private OrderDAO dao = new OrderDAO();

    public OrderDTO getOrder(int orderNumber) {
        return dao.getOrderByNumber(orderNumber);
    }

    public boolean updateOrder(int orderNumber, String newDate, int newVisitors) {
        return dao.updateOrder(orderNumber, newDate, newVisitors);
    }
}