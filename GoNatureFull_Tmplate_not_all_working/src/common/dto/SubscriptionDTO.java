package common.dto;

import java.io.Serializable;

public class SubscriptionDTO implements Serializable {
    private static final long serialVersionUID = 1L;
    private String value;
    public SubscriptionDTO(String value) { this.value = value; }
    public String getValue() { return value; }
    public String toString() { return value; }
}
