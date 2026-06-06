package common.dto;

import java.io.Serializable;

/**
 * Represents a response sent from the server to the client.
 */
public class ServerResponse implements Serializable {
    private static final long serialVersionUID = 1L;

    private boolean success;
    private String message;
    private Object data;
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

    public boolean isSuccess() {
        return success;
    }

    public String getMessage() {
        return message;
    }

    public Object getData() {
        return data;
    }

    public long getCorrelationId() {
        return correlationId;
    }

    public void setCorrelationId(long correlationId) {
        this.correlationId = correlationId;
    }
}
