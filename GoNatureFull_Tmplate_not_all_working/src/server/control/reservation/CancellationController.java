package server.control.reservation;

import server.dao.ReservationDAO;

public class CancellationController {
    public boolean cancelReservation(int reservationId) {
        return new ReservationDAO().updateStatus(reservationId, "CANCELLED");
    }
}
