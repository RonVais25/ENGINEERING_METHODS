package server.pattern.reportFactory;

import common.dto.ReportDTO;

public class CancellationsReportFactory implements ReportFactory {
    public ReportDTO createReport(String startDate, String endDate) {
        return new ReportDTO("Cancellations Report from " + startDate + " to " + endDate);
    }
}
