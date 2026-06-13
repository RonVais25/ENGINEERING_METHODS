package server.control.reservation;

import common.dto.ReservationDTO;
import org.junit.jupiter.api.Test;
import server.dao.ReservationDAO;
import testutil.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ReservationControllerMockitoTest {

    @Test
    void createReservationShouldCallDaoWhenCapacityIsAvailable() {
        ReservationDAO reservationDAO = mock(ReservationDAO.class);
        AvailabilityController availabilityController = mock(AvailabilityController.class);

        ReservationDTO expected = new ReservationDTO(
                100, 1, 2, "2026-06-20", "10:00:00",
                3, "INDIVIDUAL_PREBOOKED", "APPROVED", "QR-100"
        );

        when(availabilityController.checkAvailability(2, 3)).thenReturn(true);
        when(reservationDAO.createReservation(1, 2, "2026-06-20", "10:00:00", 3, "INDIVIDUAL_PREBOOKED"))
                .thenReturn(expected);

        ReservationController controller = new ReservationController();
        ReflectionTestUtils.setField(controller, "reservationDAO", reservationDAO);
        ReflectionTestUtils.setField(controller, "availabilityController", availabilityController);

        ReservationDTO actual = controller.createReservation(
                1, 2, "2026-06-20", "10:00:00", 3, "INDIVIDUAL_PREBOOKED"
        );

        assertSame(expected, actual);
        verify(availabilityController).checkAvailability(2, 3);
        verify(reservationDAO).createReservation(1, 2, "2026-06-20", "10:00:00", 3, "INDIVIDUAL_PREBOOKED");
    }

    @Test
    void createReservationShouldReturnNullAndNotCallDaoWhenCapacityIsNotAvailable() {
        ReservationDAO reservationDAO = mock(ReservationDAO.class);
        AvailabilityController availabilityController = mock(AvailabilityController.class);

        when(availabilityController.checkAvailability(2, 999)).thenReturn(false);

        ReservationController controller = new ReservationController();
        ReflectionTestUtils.setField(controller, "reservationDAO", reservationDAO);
        ReflectionTestUtils.setField(controller, "availabilityController", availabilityController);

        ReservationDTO actual = controller.createReservation(
                1, 2, "2026-06-20", "10:00:00", 999, "INDIVIDUAL_PREBOOKED"
        );

        assertNull(actual);
        verify(availabilityController).checkAvailability(2, 999);
        verifyNoInteractions(reservationDAO);
    }

    @Test
    void getReservationShouldDelegateToDao() {
        ReservationDAO reservationDAO = mock(ReservationDAO.class);
        ReservationDTO expected = new ReservationDTO(
                55, 1, 2, "2026-07-01", "12:00:00",
                4, "INDIVIDUAL_PREBOOKED", "APPROVED", "QR-55"
        );

        when(reservationDAO.findById(55)).thenReturn(expected);

        ReservationController controller = new ReservationController();
        ReflectionTestUtils.setField(controller, "reservationDAO", reservationDAO);

        ReservationDTO actual = controller.getReservation(55);

        assertSame(expected, actual);
        verify(reservationDAO).findById(55);
    }

    @Test
    void updateReservationShouldDelegateToDao() {
        ReservationDAO reservationDAO = mock(ReservationDAO.class);
        when(reservationDAO.updateReservation(55, "2026-07-02", 5)).thenReturn(true);

        ReservationController controller = new ReservationController();
        ReflectionTestUtils.setField(controller, "reservationDAO", reservationDAO);

        boolean result = controller.updateReservation(55, "2026-07-02", 5);

        assertTrue(result);
        verify(reservationDAO).updateReservation(55, "2026-07-02", 5);
    }
}
