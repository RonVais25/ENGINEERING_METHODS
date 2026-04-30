package common.dto;

import java.io.Serializable;

public class ServerResponse implements Serializable {
    private static final long serialVersionUID = 1L;

    private boolean success;
    private String message;
    private Object data;

    public ServerResponse(boolean success, String message) {
        this.success = success;
        this.message = message;
    }

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
}