package server.control.reservation;

import common.dto.ReservationDTO;
import server.dao.ReservationDAO;

public class ReservationController {
    private ReservationDAO reservationDAO = new ReservationDAO();
    private AvailabilityController availabilityController = new AvailabilityController();

    public ReservationDTO createReservation(int visitorId, int parkId, String date, String time, int count, String visitorType) {
        if (!availabilityController.checkAvailability(parkId, count)) return null;
        return reservationDAO.createReservation(visitorId, parkId, date, time, count, visitorType);
    }

    public ReservationDTO getReservation(int reservationId) {
        return reservationDAO.findById(reservationId);
    }

    public boolean updateReservation(int reservationId, String newDate, int newCount) {
        return reservationDAO.updateReservation(reservationId, newDate, newCount);
    }
}
