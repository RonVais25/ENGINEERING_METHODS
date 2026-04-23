package common;

import java.io.Serializable;

/**
 * Generic message envelope used for all client-server communication.
 * The 'command' field identifies the request/response type,
 * and 'data' carries the payload (Order, List<Order>, Boolean, etc.).
 *
 * Supported commands:
 *   Client -> Server : "GET_ORDERS", "UPDATE_ORDER"
 *   Server -> Client : "ORDERS_LIST", "UPDATE_RESULT", "ERROR"
 */
public class Message implements Serializable {

    private static final long serialVersionUID = 1L;

    private final String command;
    private final Object data;

    public Message(String command, Object data) {
        this.command = command;
        this.data = data;
    }

    public String getCommand() {
        return command;
    }

    public Object getData() {
        return data;
    }

    @Override
    public String toString() {
        return "Message{command='" + command + "', data=" + data + "}";
    }
}
