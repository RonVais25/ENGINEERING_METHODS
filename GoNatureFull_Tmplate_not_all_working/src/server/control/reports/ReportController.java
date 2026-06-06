package server.control.reports;

import common.dto.ReportDTO;
import server.dao.ReportDAO;

public class ReportController {
    private ReportDAO reportDAO = new ReportDAO();

    public ReportDTO generateVisitsReport(String startDate, String endDate) {
        return reportDAO.generateVisitsReport(startDate, endDate);
    }
    public ReportDTO generateUsageReport(String startDate, String endDate) {
        return reportDAO.generateUsageReport(startDate, endDate);
    }
    public ReportDTO generateCancellationsReport(String startDate, String endDate) {
        return reportDAO.generateCancellationsReport(startDate, endDate);
    }
}
