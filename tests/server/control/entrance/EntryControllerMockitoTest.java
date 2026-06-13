package server.control.entrance;

import common.dto.ReservationDTO;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;
import server.dao.ReservationDAO;
import server.dao.VisitDAO;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class EntryControllerMockitoTest {

    @Test
    void enterParkShouldRegisterEntryForApprovedReservation() {
        ReservationDTO reservation = new ReservationDTO(
                10, 1, 2, "2026-06-20", "10:00:00",
                3, "INDIVIDUAL_PREBOOKED", "APPROVED", "QR-10"
        );

        try (MockedConstruction<ReservationDAO> reservationDaoConstruction =
                     mockConstruction(ReservationDAO.class,
                             (mock, context) -> when(mock.findById(10)).thenReturn(reservation));
             MockedConstruction<VisitDAO> visitDaoConstruction =
                     mockConstruction(VisitDAO.class,
                             (mock, context) -> when(mock.registerEntry(10, 2, 3, "RESERVATION")).thenReturn(500))) {

            EntryController controller = new EntryController();

            int result = controller.enterPark(10);

            assertEquals(500, result);
            verify(reservationDaoConstruction.constructed().get(0)).findById(10);
            verify(visitDaoConstruction.constructed().get(0)).registerEntry(10, 2, 3, "RESERVATION");
        }
    }

    @Test
    void enterParkShouldDenyWhenReservationDoesNotExist() {
        try (MockedConstruction<ReservationDAO> reservationDaoConstruction =
                     mockConstruction(ReservationDAO.class,
                             (mock, context) -> when(mock.findById(10)).thenReturn(null));
             MockedConstruction<VisitDAO> visitDaoConstruction = mockConstruction(VisitDAO.class)) {

            EntryController controller = new EntryController();

            int result = controller.enterPark(10);

            assertEquals(-1, result);
            assertTrue(visitDaoConstruction.constructed().isEmpty());
        }
    }

    @Test
    void enterParkShouldDenyWhenReservationIsCancelled() {
        ReservationDTO cancelled = new ReservationDTO(
                10, 1, 2, "2026-06-20", "10:00:00",
                3, "INDIVIDUAL_PREBOOKED", "CANCELLED", "QR-10"
        );

        try (MockedConstruction<ReservationDAO> reservationDaoConstruction =
                     mockConstruction(ReservationDAO.class,
                             (mock, context) -> when(mock.findById(10)).thenReturn(cancelled));
             MockedConstruction<VisitDAO> visitDaoConstruction = mockConstruction(VisitDAO.class)) {

            EntryController controller = new EntryController();

            int result = controller.enterPark(10);

            assertEquals(-1, result);
            assertTrue(visitDaoConstruction.constructed().isEmpty());
        }
    }
}
