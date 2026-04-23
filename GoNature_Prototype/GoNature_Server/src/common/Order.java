package common;

import java.io.Serializable;
import java.sql.Date;

/**
 * Entity representing a single row in the 'Order' table.
 * This class is transmitted over the network between client and server,
 * therefore it MUST implement Serializable.
 *
 * Table schema:
 *   order_number           INT PRIMARY KEY
 *   order_date             DATE
 *   number_of_visitors     INT
 *   confirmation_code      INT
 *   subscriber_id          INT FOREIGN KEY
 *   date_of_placing_order  DATE
 */
public class Order implements Serializable {

    private static final long serialVersionUID = 1L;

    private int orderNumber;
    private Date orderDate;
    private int numberOfVisitors;
    private int confirmationCode;
    private int subscriberId;
    private Date dateOfPlacingOrder;

    public Order() {
    }

    public Order(int orderNumber,
                 Date orderDate,
                 int numberOfVisitors,
                 int confirmationCode,
                 int subscriberId,
                 Date dateOfPlacingOrder) {
        this.orderNumber = orderNumber;
        this.orderDate = orderDate;
        this.numberOfVisitors = numberOfVisitors;
        this.confirmationCode = confirmationCode;
        this.subscriberId = subscriberId;
        this.dateOfPlacingOrder = dateOfPlacingOrder;
    }

    public int getOrderNumber()                 { return orderNumber; }
    public void setOrderNumber(int v)           { this.orderNumber = v; }

    public Date getOrderDate()                  { return orderDate; }
    public void setOrderDate(Date v)            { this.orderDate = v; }

    public int getNumberOfVisitors()            { return numberOfVisitors; }
    public void setNumberOfVisitors(int v)      { this.numberOfVisitors = v; }

    public int getConfirmationCode()            { return confirmationCode; }
    public void setConfirmationCode(int v)      { this.confirmationCode = v; }

    public int getSubscriberId()                { return subscriberId; }
    public void setSubscriberId(int v)          { this.subscriberId = v; }

    public Date getDateOfPlacingOrder()         { return dateOfPlacingOrder; }
    public void setDateOfPlacingOrder(Date v)   { this.dateOfPlacingOrder = v; }

    @Override
    public String toString() {
        return "Order#" + orderNumber
                + " date=" + orderDate
                + " visitors=" + numberOfVisitors
                + " code=" + confirmationCode
                + " subscriber=" + subscriberId
                + " placed=" + dateOfPlacingOrder;
    }
}
