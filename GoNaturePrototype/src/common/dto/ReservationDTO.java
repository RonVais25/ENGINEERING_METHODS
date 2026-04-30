package common.dto;

import java.io.Serializable;

public class ReservationDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    private String reservationId;
    private int visitorId;
    private int parkId;
    private String visitDate;
    private String arrivalTime;
    private int numberOfVisitors;
    private String status;

    public ReservationDTO(String reservationId, int visitorId, int parkId,
                          String visitDate, String arrivalTime,
                          int numberOfVisitors, String status) {
        this.reservationId = reservationId;
        this.visitorId = visitorId;
        this.parkId = parkId;
        this.visitDate = visitDate;
        this.arrivalTime = arrivalTime;
        this.numberOfVisitors = numberOfVisitors;
        this.status = status;
    }

    public String getReservationId() {
        return reservationId;
    }

    public int getVisitorId() {
        return visitorId;
    }

    public int getParkId() {
        return parkId;
    }

    public String getVisitDate() {
        return visitDate;
    }

    public String getArrivalTime() {
        return arrivalTime;
    }

    public int getNumberOfVisitors() {
        return numberOfVisitors;
    }

    public String getStatus() {
        return status;
    }
}