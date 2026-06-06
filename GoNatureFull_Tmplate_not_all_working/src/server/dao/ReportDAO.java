package server.dao;

import common.dto.ReportDTO;
import common.dto.VisitDTO;
import java.util.List;

public class ReportDAO {
    public ReportDTO generateVisitsReport(String startDate, String endDate) {
        ReportDTO report = new ReportDTO("Visits Report from " + startDate + " to " + endDate);
        List<VisitDTO> visits = new VisitDAO().fetchVisits(startDate, endDate);
        if (visits.isEmpty()) report.addRow("No visit data available.");
        for (VisitDTO v : visits) report.addRow(v.toString());
        return report;
    }

    public ReportDTO generateUsageReport(String startDate, String endDate) {
        ReportDTO report = new ReportDTO("Usage Report");
        report.addRow("Usage report query placeholder - extend with occupancy calculations.");
        return report;
    }

    public ReportDTO generateCancellationsReport(String startDate, String endDate) {
        ReportDTO report = new ReportDTO("Cancellations Report");
        report.addRow("Cancellation report query placeholder - extend with cancellation statistics.");
        return report;
    }
}
