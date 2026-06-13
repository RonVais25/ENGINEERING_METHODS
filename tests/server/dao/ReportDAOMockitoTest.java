package server.dao;

import common.dto.ReportDTO;
import common.dto.VisitDTO;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ReportDAOMockitoTest {

    @Test
    void generateVisitsReportShouldAddNoDataRowWhenVisitListIsEmpty() {
        try (MockedConstruction<VisitDAO> mocked = mockConstruction(VisitDAO.class,
                (mock, context) -> when(mock.fetchVisits("2026-01-01", "2026-12-31")).thenReturn(List.of()))) {

            ReportDTO report = new ReportDAO().generateVisitsReport("2026-01-01", "2026-12-31");

            assertEquals("Visits Report from 2026-01-01 to 2026-12-31", report.getTitle());
            assertEquals(1, report.getRows().size());
            assertEquals("No visit data available.", report.getRows().get(0));
        }
    }

    @Test
    void generateVisitsReportShouldAddEachVisitAsRow() {
        VisitDTO visit = new VisitDTO(
                1, 10, 2, "2026-06-20 10:00:00",
                "2026-06-20 12:00:00", 3, 120
        );

        try (MockedConstruction<VisitDAO> mocked = mockConstruction(VisitDAO.class,
                (mock, context) -> when(mock.fetchVisits("2026-01-01", "2026-12-31")).thenReturn(List.of(visit)))) {

            ReportDTO report = new ReportDAO().generateVisitsReport("2026-01-01", "2026-12-31");

            assertEquals(1, report.getRows().size());
            assertTrue(report.getRows().get(0).contains("Visit #1"));
        }
    }

    @Test
    void usageReportShouldReturnPlaceholderRow() {
        ReportDTO report = new ReportDAO().generateUsageReport("2026-01-01", "2026-12-31");

        assertEquals("Usage Report", report.getTitle());
        assertEquals(1, report.getRows().size());
        assertTrue(report.getRows().get(0).contains("Usage report query placeholder"));
    }

    @Test
    void cancellationsReportShouldReturnPlaceholderRow() {
        ReportDTO report = new ReportDAO().generateCancellationsReport("2026-01-01", "2026-12-31");

        assertEquals("Cancellations Report", report.getTitle());
        assertEquals(1, report.getRows().size());
        assertTrue(report.getRows().get(0).contains("Cancellation report query placeholder"));
    }
}
