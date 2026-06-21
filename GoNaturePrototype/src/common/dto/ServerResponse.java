package common.dto;

import java.io.Serializable;

/**
 * Represents a response sent from the server to the client.
 */
public class ServerResponse implements Serializable {
    /** Serialization-format version identifier. */
    private static final long serialVersionUID = 1L;

    /** Whether the requested operation succeeded. */
    private boolean success;
    /** Human-readable status/error message. */
    private String message;
    /** Optional response payload (a DTO or list), or {@code null}. */
    private Object data;
    /** Echoes the request's correlation id so the client can match the reply. */
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

    /** {@return whether the operation succeeded} */
    public boolean isSuccess() {
        return success;
    }

    /** {@return the status or error message} */
    public String getMessage() {
        return message;
    }

    /** {@return the response payload, or {@code null} if none} */
    public Object getData() {
        return data;
    }

    /** {@return the correlation id matching this reply to its request} */
    public long getCorrelationId() {
        return correlationId;
    }

    /**
     * Sets the correlation id echoed back to the client.
     *
     * @param correlationId the id of the request this response answers
     */
    public void setCorrelationId(long correlationId) {
        this.correlationId = correlationId;
    }
}
