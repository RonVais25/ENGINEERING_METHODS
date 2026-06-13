package server.control.reservation;

import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;
import server.dao.ParkDAO;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AvailabilityControllerMockitoTest {

    @Test
    void checkAvailabilityShouldReturnParkDaoResult() {
        try (MockedConstruction<ParkDAO> mocked = mockConstruction(ParkDAO.class,
                (mock, context) -> when(mock.hasCapacity(2, 3)).thenReturn(true))) {

            AvailabilityController controller = new AvailabilityController();

            boolean result = controller.checkAvailability(2, 3);

            assertTrue(result);
            assertEquals(1, mocked.constructed().size());
            verify(mocked.constructed().get(0)).hasCapacity(2, 3);
        }
    }

    @Test
    void checkAvailabilityShouldReturnFalseWhenDaoReturnsFalse() {
        try (MockedConstruction<ParkDAO> mocked = mockConstruction(ParkDAO.class,
                (mock, context) -> when(mock.hasCapacity(2, 999)).thenReturn(false))) {

            AvailabilityController controller = new AvailabilityController();

            boolean result = controller.checkAvailability(2, 999);

            assertFalse(result);
            verify(mocked.constructed().get(0)).hasCapacity(2, 999);
        }
    }
}
