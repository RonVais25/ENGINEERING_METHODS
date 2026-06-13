package common.dto;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DtoFunctionalityTest {

    @Test
    void clientRequestShouldStoreAndReturnValues() {
        ClientRequest request = new ClientRequest(RequestType.CREATE_RESERVATION);

        request.put("visitorId", 1);
        request.put("parkId", 2);

        assertEquals(RequestType.CREATE_RESERVATION, request.getType());
        assertEquals(1, request.get("visitorId"));
        assertEquals(2, request.get("parkId"));
        assertNull(request.get("missing"));
    }

    @Test
    void serverResponseShouldStoreSuccessMessageAndData() {
        ParkDTO park = new ParkDTO(1, "Carmel", "Haifa", 100, 20);

        ServerResponse response = new ServerResponse(true, "OK", park);

        assertTrue(response.isSuccess());
        assertEquals("OK", response.getMessage());
        assertSame(park, response.getData());
    }

    @Test
    void reservationDtoShouldReturnAllFields() {
        ReservationDTO dto = new ReservationDTO(
                10, 1, 2, "2026-06-20", "10:00:00",
                3, "INDIVIDUAL_PREBOOKED", "APPROVED", "QR-10"
        );

        assertEquals(10, dto.getReservationId());
        assertEquals(1, dto.getVisitorId());
        assertEquals(2, dto.getParkId());
        assertEquals("2026-06-20", dto.getVisitDate());
        assertEquals("10:00:00", dto.getArrivalTime());
        assertEquals(3, dto.getNumberOfVisitors());
        assertEquals("INDIVIDUAL_PREBOOKED", dto.getVisitorType());
        assertEquals("APPROVED", dto.getStatus());
        assertEquals("QR-10", dto.getQrCode());
        assertTrue(dto.toString().contains("Reservation #10"));
    }

    @Test
    void reportDtoShouldAddRowsInOrder() {
        ReportDTO report = new ReportDTO("Visits Report");

        report.addRow("row 1");
        report.addRow("row 2");

        assertEquals("Visits Report", report.getTitle());
        assertEquals(List.of("row 1", "row 2"), report.getRows());
        assertTrue(report.toString().contains("row 1"));
        assertTrue(report.toString().contains("row 2"));
    }
}
