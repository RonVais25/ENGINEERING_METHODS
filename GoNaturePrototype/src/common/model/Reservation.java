package common.model;

public class Reservation {
    private String reservationId;
    private String parkName;
    private String date;
    private String time;
    private int numberOfVisitors;
    private boolean active;

    public Reservation(String reservationId, String parkName, String date, String time, int numberOfVisitors) {
        this.reservationId = reservationId;
        this.parkName = parkName;
        this.date = date;
        this.time = time;
        this.numberOfVisitors = numberOfVisitors;
        this.active = true;
    }

    public String getReservationId() {
        return reservationId;
    }

    public String getParkName() {
        return parkName;
    }

    public String getDate() {
        return date;
    }

    public String getTime() {
        return time;
    }

    public int getNumberOfVisitors() {
        return numberOfVisitors;
    }

    public boolean isActive() {
        return active;
    }

    public void cancel() {
        this.active = false;
    }
}