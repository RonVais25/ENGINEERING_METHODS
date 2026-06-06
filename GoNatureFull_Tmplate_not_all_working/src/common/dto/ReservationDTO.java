package common.dto;

import java.io.Serializable;

public class ReservationDTO implements Serializable {
    private static final long serialVersionUID = 1L;
    private int reservationId;
    private int visitorId;
    private int parkId;
    private String visitDate;
    private String arrivalTime;
    private int numberOfVisitors;
    private String visitorType;
    private String status;
    private String qrCode;

    public ReservationDTO(int reservationId, int visitorId, int parkId, String visitDate, String arrivalTime,
                          int numberOfVisitors, String visitorType, String status, String qrCode) {
        this.reservationId = reservationId;
        this.visitorId = visitorId;
        this.parkId = parkId;
        this.visitDate = visitDate;
        this.arrivalTime = arrivalTime;
        this.numberOfVisitors = numberOfVisitors;
        this.visitorType = visitorType;
        this.status = status;
        this.qrCode = qrCode;
    }

    public int getReservationId() { return reservationId; }
    public int getVisitorId() { return visitorId; }
    public int getParkId() { return parkId; }
    public String getVisitDate() { return visitDate; }
    public String getArrivalTime() { return arrivalTime; }
    public int getNumberOfVisitors() { return numberOfVisitors; }
    public String getVisitorType() { return visitorType; }
    public String getStatus() { return status; }
    public String getQrCode() { return qrCode; }

    @Override
    public String toString() {
        return "Reservation #" + reservationId + " park=" + parkId + " date=" + visitDate + " time=" + arrivalTime +
                " visitors=" + numberOfVisitors + " status=" + status + " QR=" + qrCode;
    }
}
