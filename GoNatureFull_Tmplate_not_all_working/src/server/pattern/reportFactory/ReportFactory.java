package server.pattern.reportFactory;

import common.dto.ReportDTO;

public interface ReportFactory {
    ReportDTO createReport(String startDate, String endDate);
}
