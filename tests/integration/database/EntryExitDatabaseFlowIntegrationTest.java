package integration.database;

import common.dto.ReservationDTO;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import server.dao.ReservationDAO;
import server.dao.VisitDAO;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Checks real DB behavior for park entry and exit.
 */
@Disabled("Requires real MySQL database with schema.sql and seed.sql loaded")
class EntryExitDatabaseFlowIntegrationTest {

    @Test
    void registerEntryAndExitShouldWorkInDatabase() {
        ReservationDAO reservationDAO = new ReservationDAO();
        VisitDAO visitDAO = new VisitDAO();

        ReservationDTO reservation = reservationDAO.createReservation(
                1,
                1,
                LocalDate.now().plusDays(12).toString(),
                "09:00:00",
                2,
                "INDIVIDUAL_PREBOOKED"
        );

        assertNotNull(reservation);

        int visitId = visitDAO.registerEntry(
                reservation.getReservationId(),
                reservation.getParkId(),
                reservation.getNumberOfVisitors(),
                "RESERVATION"
        );

        assertTrue(visitId > 0);

        boolean exitRecorded = visitDAO.registerExit(reservation.getReservationId());

        assertTrue(exitRecorded);
    }
}
