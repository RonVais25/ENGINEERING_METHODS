package common.dto;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

/**
 * Represents a request sent from the client to the server.
 */
public class ClientRequest implements Serializable {
    /** Serialization-format version identifier. */
    private static final long serialVersionUID = 1L;

    /** What the client is asking the server to do. */
    private RequestType type;
    /** Named request parameters keyed by name. */
    private Map<String, Object> data;
    /** Client-assigned id used to match the server's response to this request. */
    private long correlationId = 0L;

    /**
     * Creates a new request with the given type.
     *
     * @param type request type
     */
    public ClientRequest(RequestType type) {
        this.type = type;
        this.data = new HashMap<>();
    }

    /** {@return the type of this request} */
    public RequestType getType() {
        return type;
    }

    /**
     * Adds data to the request payload.
     *
     * @param key data key
     * @param value data value
     */
    public void put(String key, Object value) {
        data.put(key, value);
    }

    /**
     * Returns data stored under the given key.
     *
     * @param key data key
     * @return matching value
     */
    public Object get(String key) {
        return data.get(key);
    }

    /** {@return the correlation id matching this request to its response} */
    public long getCorrelationId() {
        return correlationId;
    }

    /**
     * Sets the correlation id used to match the server's response.
     *
     * @param correlationId the id to assign to this request
     */
    public void setCorrelationId(long correlationId) {
        this.correlationId = correlationId;
    }
}
