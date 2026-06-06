package common.dto;

import java.io.Serializable;

public class ParkDTO implements Serializable {
    private static final long serialVersionUID = 1L;
    private int parkId;
    private String parkName;
    private String location;
    private int maximumCapacity;
    private int currentVisitors;

    public ParkDTO(int parkId, String parkName, String location, int maximumCapacity, int currentVisitors) {
        this.parkId = parkId;
        this.parkName = parkName;
        this.location = location;
        this.maximumCapacity = maximumCapacity;
        this.currentVisitors = currentVisitors;
    }

    public int getParkId() { return parkId; }
    public String getParkName() { return parkName; }
    public String getLocation() { return location; }
    public int getMaximumCapacity() { return maximumCapacity; }
    public int getCurrentVisitors() { return currentVisitors; }
    public String toString() { return parkId + " - " + parkName + " (" + currentVisitors + "/" + maximumCapacity + ")"; }
}
