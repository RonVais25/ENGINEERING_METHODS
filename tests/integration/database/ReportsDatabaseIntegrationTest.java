package integration.database;

import common.dto.ReportDTO;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import server.dao.ReportDAO;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Checks that report DAO methods work with a real database.
 */
@Disabled("Requires real MySQL database with schema.sql and seed.sql loaded")
class ReportsDatabaseIntegrationTest {

    @Test
    void visitsReportShouldReturnReportObject() {
        ReportDTO report = new ReportDAO().generateVisitsReport("2026-01-01", "2026-12-31");

        assertNotNull(report);
        assertTrue(report.getTitle().contains("Visits Report"));
        assertNotNull(report.getRows());
    }

    @Test
    void usageReportShouldReturnReportObject() {
        ReportDTO report = new ReportDAO().generateUsageReport("2026-01-01", "2026-12-31");

        assertNotNull(report);
        assertEquals("Usage Report", report.getTitle());
    }

    @Test
    void cancellationsReportShouldReturnReportObject() {
        ReportDTO report = new ReportDAO().generateCancellationsReport("2026-01-01", "2026-12-31");

        assertNotNull(report);
        assertEquals("Cancellations Report", report.getTitle());
    }
}
