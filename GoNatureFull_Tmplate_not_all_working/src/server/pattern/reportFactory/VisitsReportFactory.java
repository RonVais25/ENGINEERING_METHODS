package server.pattern.reportFactory;

import common.dto.ReportDTO;

public class VisitsReportFactory implements ReportFactory {
    public ReportDTO createReport(String startDate, String endDate) {
        return new ReportDTO("Visits Report from " + startDate + " to " + endDate);
    }
}
