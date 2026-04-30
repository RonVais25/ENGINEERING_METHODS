package server.control;

import java.time.LocalTime;

import common.model.Park;
import common.model.Reservation;
import common.model.VisitRecord;
import server.dao.InMemoryDatabase;
import server.dao.VisitDAO;

public class EntryController {

    private VisitDAO visitDAO = new VisitDAO();

    public VisitRecord enterPark(String reservationId) {
        Reservation reservation = InMemoryDatabase.reservations.get(reservationId);

        if (reservation == null || !reservation.isActive()) {
            System.out.println("Invalid reservation. Entry denied.");
            return null;
        }

        Park park = InMemoryDatabase.parks.get(reservation.getParkName());

        if (park == null || !park.hasCapacity(reservation.getNumberOfVisitors())) {
            System.out.println("Park has no available capacity. Entry denied.");
            return null;
        }

        park.addVisitors(reservation.getNumberOfVisitors());

        VisitRecord visit = new VisitRecord(
                reservationId,
                LocalTime.now().toString()
        );

        InMemoryDatabase.visits.put(reservationId, visit);

        // Save entry to MySQL
        visitDAO.registerEntry(
                reservationId,
                1, // demo parkId
                reservation.getNumberOfVisitors()
        );

        System.out.println("Entry approved.");
        System.out.println("Current visitors in " + park.getName() + ": " + park.getCurrentVisitors());

        return visit;
    }

    public boolean exitPark(String reservationId) {
        VisitRecord visit = InMemoryDatabase.visits.get(reservationId);

        if (visit == null) {
            System.out.println("Visit record not found.");
            return false;
        }

        if (visit.getExitTime() != null) {
            System.out.println("Visitor already exited.");
            return false;
        }

        Reservation reservation = InMemoryDatabase.reservations.get(reservationId);

        if (reservation == null) {
            System.out.println("Reservation not found. Exit cannot be recorded.");
            return false;
        }

        Park park = InMemoryDatabase.parks.get(reservation.getParkName());

        if (park == null) {
            System.out.println("Park not found. Exit cannot be recorded.");
            return false;
        }

        park.removeVisitors(reservation.getNumberOfVisitors());
        visit.setExitTime(LocalTime.now().toString());

        // Save exit to MySQL
        visitDAO.registerExitByReservationId(reservationId);

        System.out.println("Exit recorded.");
        System.out.println("Current visitors in " + park.getName() + ": " + park.getCurrentVisitors());

        return true;
    }
}