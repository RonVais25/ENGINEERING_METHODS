package common.dto;

import java.io.Serializable;

public class OrderDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    private int orderNumber;
    private String orderDate;
    private int numberOfVisitors;
    private int confirmationCode;
    private int subscriberId;
    private String dateOfPlacingOrder;

    public OrderDTO(int orderNumber, String orderDate, int numberOfVisitors,
                    int confirmationCode, int subscriberId, String dateOfPlacingOrder) {
        this.orderNumber = orderNumber;
        this.orderDate = orderDate;
        this.numberOfVisitors = numberOfVisitors;
        this.confirmationCode = confirmationCode;
        this.subscriberId = subscriberId;
        this.dateOfPlacingOrder = dateOfPlacingOrder;
    }

    public int getOrderNumber() {
        return orderNumber;
    }

    public String getOrderDate() {
        return orderDate;
    }

    public int getNumberOfVisitors() {
        return numberOfVisitors;
    }

    public int getConfirmationCode() {
        return confirmationCode;
    }

    public int getSubscriberId() {
        return subscriberId;
    }

    public String getDateOfPlacingOrder() {
        return dateOfPlacingOrder;
    }
}
