package common.dto;

import java.io.Serializable;

/**
 * One row in the Usage Report, showing a park/day in which the park was not
 * fully occupied according to recorded visits.
 */
public class UsageReportRow implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String date;
    private final int parkId;
    private final String parkName;
    private final int totalVisitors;
    private final int maxCapacity;
    private final int unusedCapacity;

    /**
     * Creates a usage report row.
     *
     * @param date           visit date, ISO {@code yyyy-MM-dd}
     * @param parkId         park identifier
     * @param parkName       park display name
     * @param totalVisitors  total recorded visitors for the day
     * @param maxCapacity    park maximum capacity
     * @param unusedCapacity max capacity minus recorded visitors
     */
    public UsageReportRow(String date, int parkId, String parkName,
                          int totalVisitors, int maxCapacity, int unusedCapacity) {
        this.date = date;
        this.parkId = parkId;
        this.parkName = parkName;
        this.totalVisitors = totalVisitors;
        this.maxCapacity = maxCapacity;
        this.unusedCapacity = unusedCapacity;
    }

    public String getDate() { return date; }
    public int getParkId() { return parkId; }
    public String getParkName() { return parkName; }
    public int getTotalVisitors() { return totalVisitors; }
    public int getMaxCapacity() { return maxCapacity; }
    public int getUnusedCapacity() { return unusedCapacity; }
}
