package common.dto;

import java.io.Serializable;

/**
 * Immutable data transfer object representing a single park-visit order.
 *
 * <p>Instances are created server-side by {@link server.dao.OrderDAO} from a database
 * row and serialized across the socket to the client inside a
 * {@link common.dto.ServerResponse}. Date fields are carried as ISO {@code yyyy-MM-dd}
 * strings to avoid coupling the wire format to {@code java.sql} types.
 */
public class OrderDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    /** Unique order identifier (primary key). */
    private int orderNumber;
    /** Scheduled visit date, ISO {@code yyyy-MM-dd}. */
    private String orderDate;
    /** Number of visitors on the order. */
    private int numberOfVisitors;
    /** Booking confirmation code shown to the subscriber. */
    private int confirmationCode;
    /** Identifier of the subscriber who owns the order. */
    private int subscriberId;
    /** Date the order was placed, ISO {@code yyyy-MM-dd}. */
    private String dateOfPlacingOrder;

    /**
     * Creates a fully populated order.
     *
     * @param orderNumber        unique order identifier
     * @param orderDate          scheduled visit date, ISO {@code yyyy-MM-dd}
     * @param numberOfVisitors   number of visitors on the order
     * @param confirmationCode   booking confirmation code
     * @param subscriberId       identifier of the owning subscriber
     * @param dateOfPlacingOrder date the order was placed, ISO {@code yyyy-MM-dd}
     */
    public OrderDTO(int orderNumber, String orderDate, int numberOfVisitors,
                    int confirmationCode, int subscriberId, String dateOfPlacingOrder) {
        this.orderNumber = orderNumber;
        this.orderDate = orderDate;
        this.numberOfVisitors = numberOfVisitors;
        this.confirmationCode = confirmationCode;
        this.subscriberId = subscriberId;
        this.dateOfPlacingOrder = dateOfPlacingOrder;
    }

    /** @return the unique order identifier */
    public int getOrderNumber() {
        return orderNumber;
    }

    /** @return the scheduled visit date, ISO {@code yyyy-MM-dd} */
    public String getOrderDate() {
        return orderDate;
    }

    /** @return the number of visitors on the order */
    public int getNumberOfVisitors() {
        return numberOfVisitors;
    }

    /** @return the booking confirmation code */
    public int getConfirmationCode() {
        return confirmationCode;
    }

    /** @return the identifier of the owning subscriber */
    public int getSubscriberId() {
        return subscriberId;
    }

    /** @return the date the order was placed, ISO {@code yyyy-MM-dd} */
    public String getDateOfPlacingOrder() {
        return dateOfPlacingOrder;
    }
}
