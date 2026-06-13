package server.net;

import common.dto.ClientRequest;
import common.dto.RequestType;
import common.dto.ReservationDTO;
import common.dto.ServerResponse;
import common.dto.ReportDTO;
import org.junit.jupiter.api.Test;
import server.control.entrance.EntryController;
import server.control.entrance.ExitController;
import server.control.reports.ReportController;
import server.control.reservation.CancellationController;
import server.control.reservation.ReservationController;
import testutil.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RequestRouterMockitoTest {

    @Test
    void createReservationShouldReturnSuccessWhenControllerReturnsReservation() {
        ReservationController reservationController = mock(ReservationController.class);
        ReservationDTO reservation = new ReservationDTO(
                10, 1, 2, "2026-06-20", "10:00:00",
                3, "INDIVIDUAL_PREBOOKED", "APPROVED", "QR-10"
        );

        when(reservationController.createReservation(1, 2, "2026-06-20", "10:00:00", 3, "INDIVIDUAL_PREBOOKED"))
                .thenReturn(reservation);

        RequestRouter router = new RequestRouter();
        ReflectionTestUtils.setField(router, "reservationController", reservationController);

        ClientRequest request = new ClientRequest(RequestType.CREATE_RESERVATION);
        request.put("visitorId", 1);
        request.put("parkId", 2);
        request.put("date", "2026-06-20");
        request.put("time", "10:00:00");
        request.put("numberOfVisitors", 3);
        request.put("visitorType", "INDIVIDUAL_PREBOOKED");

        ServerResponse response = router.handle(request);

        assertTrue(response.isSuccess());
        assertEquals("Reservation created.", response.getMessage());
        assertSame(reservation, response.getData());
    }

    @Test
    void createReservationShouldReturnFailureWhenControllerReturnsNull() {
        ReservationController reservationController = mock(ReservationController.class);
        when(reservationController.createReservation(1, 2, "2026-06-20", "10:00:00", 9999, "INDIVIDUAL_PREBOOKED"))
                .thenReturn(null);

        RequestRouter router = new RequestRouter();
        ReflectionTestUtils.setField(router, "reservationController", reservationController);

        ClientRequest request = new ClientRequest(RequestType.CREATE_RESERVATION);
        request.put("visitorId", 1);
        request.put("parkId", 2);
        request.put("date", "2026-06-20");
        request.put("time", "10:00:00");
        request.put("numberOfVisitors", 9999);
        request.put("visitorType", "INDIVIDUAL_PREBOOKED");

        ServerResponse response = router.handle(request);

        assertFalse(response.isSuccess());
        assertEquals("Reservation failed: no availability or invalid data.", response.getMessage());
    }

    @Test
    void getReservationShouldReturnNotFoundWhenControllerReturnsNull() {
        ReservationController reservationController = mock(ReservationController.class);
        when(reservationController.getReservation(88)).thenReturn(null);

        RequestRouter router = new RequestRouter();
        ReflectionTestUtils.setField(router, "reservationController", reservationController);

        ClientRequest request = new ClientRequest(RequestType.GET_RESERVATION);
        request.put("reservationId", 88);

        ServerResponse response = router.handle(request);

        assertFalse(response.isSuccess());
        assertEquals("Reservation not found.", response.getMessage());
    }

    @Test
    void cancelReservationShouldReturnControllerResult() {
        CancellationController cancellationController = mock(CancellationController.class);
        when(cancellationController.cancelReservation(10)).thenReturn(true);

        RequestRouter router = new RequestRouter();
        ReflectionTestUtils.setField(router, "cancellationController", cancellationController);

        ClientRequest request = new ClientRequest(RequestType.CANCEL_RESERVATION);
        request.put("reservationId", 10);

        ServerResponse response = router.handle(request);

        assertTrue(response.isSuccess());
        assertEquals("Reservation cancelled.", response.getMessage());
        verify(cancellationController).cancelReservation(10);
    }

    @Test
    void enterParkShouldApproveWhenVisitIdIsPositive() {
        EntryController entryController = mock(EntryController.class);
        when(entryController.enterPark(10)).thenReturn(500);

        RequestRouter router = new RequestRouter();
        ReflectionTestUtils.setField(router, "entryController", entryController);

        ClientRequest request = new ClientRequest(RequestType.ENTER_PARK);
        request.put("reservationId", 10);

        ServerResponse response = router.handle(request);

        assertTrue(response.isSuccess());
        assertEquals("Entry approved. Visit ID: 500", response.getMessage());
    }

    @Test
    void exitParkShouldReturnExitRecordedWhenControllerReturnsTrue() {
        ExitController exitController = mock(ExitController.class);
        when(exitController.exitPark(10)).thenReturn(true);

        RequestRouter router = new RequestRouter();
        ReflectionTestUtils.setField(router, "exitController", exitController);

        ClientRequest request = new ClientRequest(RequestType.EXIT_PARK);
        request.put("reservationId", 10);

        ServerResponse response = router.handle(request);

        assertTrue(response.isSuccess());
        assertEquals("Exit recorded.", response.getMessage());
    }

    @Test
    void generateVisitsReportShouldReturnReportFromController() {
        ReportController reportController = mock(ReportController.class);
        ReportDTO report = new ReportDTO("Visits Report");
        when(reportController.generateVisitsReport("2026-01-01", "2026-12-31")).thenReturn(report);

        RequestRouter router = new RequestRouter();
        ReflectionTestUtils.setField(router, "reportController", reportController);

        ClientRequest request = new ClientRequest(RequestType.GENERATE_VISITS_REPORT);
        request.put("startDate", "2026-01-01");
        request.put("endDate", "2026-12-31");

        ServerResponse response = router.handle(request);

        assertTrue(response.isSuccess());
        assertEquals("Visits report generated.", response.getMessage());
        assertSame(report, response.getData());
    }

    @Test
    void missingRequestDataShouldReturnServerErrorInsteadOfThrowingToCaller() {
        RequestRouter router = new RequestRouter();

        ClientRequest request = new ClientRequest(RequestType.CREATE_RESERVATION);

        ServerResponse response = router.handle(request);

        assertFalse(response.isSuccess());
        assertTrue(response.getMessage().startsWith("Server error:"));
    }
}
