package common.dto;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

/**
 * Represents a request sent from the client to the server.
 */
public class ClientRequest implements Serializable {
/** Serialization identifier for this class. */
    private static final long serialVersionUID = 1L;
/** Stores the type value used by this component. */

    private RequestType type;
/** Stores the data value used by this component. */
    private Map<String, Object> data;
/** Stores the correlation id value used by this component. */
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
/**
 * Returns the type.
 * @return the result produced by the operation
 */

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
