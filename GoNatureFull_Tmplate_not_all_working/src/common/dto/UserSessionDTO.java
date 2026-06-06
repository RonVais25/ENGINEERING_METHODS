package common.dto;

import java.io.Serializable;

public class UserSessionDTO implements Serializable {
    private static final long serialVersionUID = 1L;
    private String value;
    public UserSessionDTO(String value) { this.value = value; }
    public String getValue() { return value; }
    public String toString() { return value; }
}
