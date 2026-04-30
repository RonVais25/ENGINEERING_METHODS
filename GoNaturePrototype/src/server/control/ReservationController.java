package server.control;

import java.util.UUID;
import common.dto.ReservationDTO;
import common.model.Park;
import common.model.Reservation;
import server.dao.InMemoryDatabase;
import server.dao.ReservationDAO;

public class ReservationController {

    public Reservation createReservation(String parkName, String date, String time, int visitorsCount) {
        Park park = InMemoryDatabase.parks.get(parkName);

        if (park == null) {
            System.out.println("Park not found.");
            return null;
        }

        if (!park.hasCapacity(visitorsCount)) {
            System.out.println("No available capacity for this reservation.");
            return null;
        }

        String reservationId = UUID.randomUUID().toString();

        Reservation reservation = new Reservation(
                reservationId,
                parkName,
                date,
                time,
                visitorsCount
        );

        InMemoryDatabase.reservations.put(reservationId, reservation);

        ReservationDAO dao = new ReservationDAO();

        dao.saveReservation(
                reservationId,
                1,   // visitorId for demo
                1,   // parkId for demo
                date,
                time,
                visitorsCount
        );

        System.out.println("Reservation created successfully.");
        System.out.println("Reservation ID: " + reservationId);
        System.out.println("QR Code: QR-" + reservationId);

        return reservation;
    }

    public boolean cancelReservation(String reservationId) {
        Reservation reservation = InMemoryDatabase.reservations.get(reservationId);

        if (reservation == null || !reservation.isActive()) {
            System.out.println("Reservation not found or already cancelled.");
            return false;
        }

        reservation.cancel();
        System.out.println("Reservation cancelled successfully.");
        return true;
    }
    public ReservationDTO getReservationFromDB(String reservationId) {
        ReservationDAO dao = new ReservationDAO();
        return dao.getReservationById(reservationId);
    }

    public boolean updateReservationInDB(String reservationId, String newDate, int newNumberOfVisitors) {
        ReservationDAO dao = new ReservationDAO();
        return dao.updateReservation(reservationId, newDate, newNumberOfVisitors);
    }
}