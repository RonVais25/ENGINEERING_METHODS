package common.dto;

import java.io.Serializable;

public class VisitDTO implements Serializable {
    private static final long serialVersionUID = 1L;
    private int visitId;
    private int reservationId;
    private int parkId;
    private String entryTime;
    private String exitTime;
    private int visitorsCount;
    private long stayDurationMinutes;

    public VisitDTO(int visitId, int reservationId, int parkId, String entryTime, String exitTime, int visitorsCount, long stayDurationMinutes) {
        this.visitId = visitId;
        this.reservationId = reservationId;
        this.parkId = parkId;
        this.entryTime = entryTime;
        this.exitTime = exitTime;
        this.visitorsCount = visitorsCount;
        this.stayDurationMinutes = stayDurationMinutes;
    }
    public int getVisitId() { return visitId; }
    public int getReservationId() { return reservationId; }
    public int getParkId() { return parkId; }
    public String getEntryTime() { return entryTime; }
    public String getExitTime() { return exitTime; }
    public int getVisitorsCount() { return visitorsCount; }
    public long getStayDurationMinutes() { return stayDurationMinutes; }
    public String toString() { return "Visit #" + visitId + " reservation=" + reservationId + " entry=" + entryTime + " exit=" + exitTime + " duration=" + stayDurationMinutes + " minutes"; }
}
