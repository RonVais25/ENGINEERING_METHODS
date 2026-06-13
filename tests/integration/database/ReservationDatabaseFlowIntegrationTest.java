package integration.database;

import common.dto.ReservationDTO;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import server.dao.ParkDAO;
import server.dao.ReservationDAO;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Checks real DB behavior for the required private reservation scenario.
 */
@Disabled("Requires real MySQL database with schema.sql and seed.sql loaded")
class ReservationDatabaseFlowIntegrationTest {

    @Test
    void parkCapacityQueryShouldReturnTrueForSmallPrivateGroup() {
        ParkDAO parkDAO = new ParkDAO();

        boolean available = parkDAO.hasCapacity(1, 3);

        assertTrue(available);
    }

    @Test
    void reservationShouldBeInsertedAndLoadedFromDatabase() {
        ReservationDAO reservationDAO = new ReservationDAO();
        String date = LocalDate.now().plusDays(10).toString();

        ReservationDTO created = reservationDAO.createReservation(
                1,
                1,
                date,
                "10:00:00",
                3,
                "INDIVIDUAL_PREBOOKED"
        );

        assertNotNull(created);
        assertTrue(created.getReservationId() > 0);
        assertEquals(1, created.getVisitorId());
        assertEquals(1, created.getParkId());
        assertEquals(3, created.getNumberOfVisitors());
        assertEquals("APPROVED", created.getStatus());
        assertNotNull(created.getQrCode());

        ReservationDTO loaded = reservationDAO.findById(created.getReservationId());

        assertNotNull(loaded);
        assertEquals(created.getReservationId(), loaded.getReservationId());
    }

    @Test
    void cancelReservationShouldUpdateStatusInDatabase() {
        ReservationDAO reservationDAO = new ReservationDAO();
        String date = LocalDate.now().plusDays(11).toString();

        ReservationDTO created = reservationDAO.createReservation(
                1,
                1,
                date,
                "12:00:00",
                2,
                "INDIVIDUAL_PREBOOKED"
        );

        assertNotNull(created);

        boolean cancelled = reservationDAO.updateStatus(created.getReservationId(), "CANCELLED");
        ReservationDTO afterCancel = reservationDAO.findById(created.getReservationId());

        assertTrue(cancelled);
        assertEquals("CANCELLED", afterCancel.getStatus());
    }
}
