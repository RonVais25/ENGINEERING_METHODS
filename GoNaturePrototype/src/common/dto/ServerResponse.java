package common.dto;

import java.io.Serializable;

/**
 * Represents a response sent from the server to the client.
 */
public class ServerResponse implements Serializable {
/** Serialization identifier for this class. */
    private static final long serialVersionUID = 1L;
/** Stores the success value used by this component. */

    private boolean success;
/** Stores the message value used by this component. */
    private String message;
/** Stores the data value used by this component. */
    private Object data;
/** Stores the correlation id value used by this component. */
    private long correlationId = 0L;

    /**
     * Creates a response without additional data.
     *
     * @param success operation status
     * @param message response message
     */
    public ServerResponse(boolean success, String message) {
        this.success = success;
        this.message = message;
    }

    /**
     * Creates a response with additional data.
     *
     * @param success operation status
     * @param message response message
     * @param data response payload
     */
    public ServerResponse(boolean success, String message, Object data) {
        this.success = success;
        this.message = message;
        this.data = data;
    }
/**
 * Indicates whether the success condition is true.
 * @return the result produced by the operation
 */

    public boolean isSuccess() {
        return success;
    }
/**
 * Returns the message.
 * @return the result produced by the operation
 */

    public String getMessage() {
        return message;
    }
/**
 * Returns the data.
 * @return the result produced by the operation
 */

    public Object getData() {
        return data;
    }
/**
 * Returns the correlation id.
 * @return the result produced by the operation
 */

    public long getCorrelationId() {
        return correlationId;
    }
/**
 * Sets the correlation id.
 * @param correlationId value supplied to the operation
 */

    public void setCorrelationId(long correlationId) {
        this.correlationId = correlationId;
    }
}
