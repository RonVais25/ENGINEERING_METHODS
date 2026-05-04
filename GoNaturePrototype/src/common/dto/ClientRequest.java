package common.dto;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

public class ClientRequest implements Serializable {
    private static final long serialVersionUID = 1L;

    private RequestType type;
    private Map<String, Object> data;

    public ClientRequest(RequestType type) {
        this.type = type;
        this.data = new HashMap<>();
    }

    public RequestType getType() {
        return type;
    }

    public void put(String key, Object value) {
        data.put(key, value);
    }

    public Object get(String key) {
        return data.get(key);
    }
}