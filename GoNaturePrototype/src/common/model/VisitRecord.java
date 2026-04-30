package common.model;

public class VisitRecord {
    private String reservationId;
    private String entryTime;
    private String exitTime;

    public VisitRecord(String reservationId, String entryTime) {
        this.reservationId = reservationId;
        this.entryTime = entryTime;
    }

    public String getReservationId() {
        return reservationId;
    }

    public String getEntryTime() {
        return entryTime;
    }

    public String getExitTime() {
        return exitTime;
    }

    public void setExitTime(String exitTime) {
        this.exitTime = exitTime;
    }

    public String calculateStayDuration() {
        if (exitTime == null) {
            return "Ongoing";
        }
        return entryTime + " - " + exitTime;
    }
}