package server.control.entrance;

import common.dto.ReservationDTO;
import server.dao.ReservationDAO;
import server.dao.VisitDAO;

public class EntryController {
    public int enterPark(int reservationId) {
        ReservationDTO reservation = new ReservationDAO().findById(reservationId);
        if (reservation == null || !"APPROVED".equals(reservation.getStatus())) return -1;
        return new VisitDAO().registerEntry(reservationId, reservation.getParkId(), reservation.getNumberOfVisitors(), "RESERVATION");
    }
}
