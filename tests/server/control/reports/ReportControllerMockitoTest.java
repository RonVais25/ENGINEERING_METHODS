package server.control.reports;

import common.dto.ReportDTO;
import org.junit.jupiter.api.Test;
import server.dao.ReportDAO;
import testutil.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ReportControllerMockitoTest {

    @Test
    void generateVisitsReportShouldDelegateToReportDao() {
        ReportDAO reportDAO = mock(ReportDAO.class);
        ReportDTO expected = new ReportDTO("Visits Report");
        when(reportDAO.generateVisitsReport("2026-01-01", "2026-12-31")).thenReturn(expected);

        ReportController controller = new ReportController();
        ReflectionTestUtils.setField(controller, "reportDAO", reportDAO);

        ReportDTO actual = controller.generateVisitsReport("2026-01-01", "2026-12-31");

        assertSame(expected, actual);
        verify(reportDAO).generateVisitsReport("2026-01-01", "2026-12-31");
    }

    @Test
    void generateUsageReportShouldDelegateToReportDao() {
        ReportDAO reportDAO = mock(ReportDAO.class);
        ReportDTO expected = new ReportDTO("Usage Report");
        when(reportDAO.generateUsageReport("2026-01-01", "2026-12-31")).thenReturn(expected);

        ReportController controller = new ReportController();
        ReflectionTestUtils.setField(controller, "reportDAO", reportDAO);

        ReportDTO actual = controller.generateUsageReport("2026-01-01", "2026-12-31");

        assertSame(expected, actual);
        verify(reportDAO).generateUsageReport("2026-01-01", "2026-12-31");
    }

    @Test
    void generateCancellationsReportShouldDelegateToReportDao() {
        ReportDAO reportDAO = mock(ReportDAO.class);
        ReportDTO expected = new ReportDTO("Cancellations Report");
        when(reportDAO.generateCancellationsReport("2026-01-01", "2026-12-31")).thenReturn(expected);

        ReportController controller = new ReportController();
        ReflectionTestUtils.setField(controller, "reportDAO", reportDAO);

        ReportDTO actual = controller.generateCancellationsReport("2026-01-01", "2026-12-31");

        assertSame(expected, actual);
        verify(reportDAO).generateCancellationsReport("2026-01-01", "2026-12-31");
    }
}
