package server.control.reservation;

import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;
import server.dao.ReservationDAO;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CancellationControllerMockitoTest {

    @Test
    void cancelReservationShouldUpdateStatusToCancelled() {
        try (MockedConstruction<ReservationDAO> mocked = mockConstruction(ReservationDAO.class,
                (mock, context) -> when(mock.updateStatus(77, "CANCELLED")).thenReturn(true))) {

            CancellationController controller = new CancellationController();

            boolean result = controller.cancelReservation(77);

            assertTrue(result);
            verify(mocked.constructed().get(0)).updateStatus(77, "CANCELLED");
        }
    }

    @Test
    void cancelReservationShouldReturnFalseWhenDaoFails() {
        try (MockedConstruction<ReservationDAO> mocked = mockConstruction(ReservationDAO.class,
                (mock, context) -> when(mock.updateStatus(77, "CANCELLED")).thenReturn(false))) {

            CancellationController controller = new CancellationController();

            boolean result = controller.cancelReservation(77);

            assertFalse(result);
            verify(mocked.constructed().get(0)).updateStatus(77, "CANCELLED");
        }
    }
}
