package server.pattern.reportFactory;

import common.dto.ReportDTO;

public class UsageReportFactory implements ReportFactory {
    public ReportDTO createReport(String startDate, String endDate) {
        return new ReportDTO("Usage Report from " + startDate + " to " + endDate);
    }
}
