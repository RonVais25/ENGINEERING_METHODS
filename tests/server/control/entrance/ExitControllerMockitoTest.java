package server.control.entrance;

import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;
import server.dao.VisitDAO;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ExitControllerMockitoTest {

    @Test
    void exitParkShouldDelegateToVisitDao() {
        try (MockedConstruction<VisitDAO> mocked = mockConstruction(VisitDAO.class,
                (mock, context) -> when(mock.registerExit(10)).thenReturn(true))) {

            ExitController controller = new ExitController();

            boolean result = controller.exitPark(10);

            assertTrue(result);
            verify(mocked.constructed().get(0)).registerExit(10);
        }
    }

    @Test
    void exitParkShouldReturnFalseWhenDaoFails() {
        try (MockedConstruction<VisitDAO> mocked = mockConstruction(VisitDAO.class,
                (mock, context) -> when(mock.registerExit(10)).thenReturn(false))) {

            ExitController controller = new ExitController();

            boolean result = controller.exitPark(10);

            assertFalse(result);
            verify(mocked.constructed().get(0)).registerExit(10);
        }
    }
}
