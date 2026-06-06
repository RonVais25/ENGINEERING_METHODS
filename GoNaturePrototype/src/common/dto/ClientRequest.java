package common.dto;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

/**
 * Represents a request sent from the client to the server.
 */
public class ClientRequest implements Serializable {
    private static final long serialVersionUID = 1L;

    private RequestType type;
    private Map<String, Object> data;
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

    public long getCorrelationId() {
        return correlationId;
    }

    public void setCorrelationId(long correlationId) {
        this.correlationId = correlationId;
    }
}
