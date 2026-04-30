package server.control;

import server.dao.ReportDAO;

public class ReportController {

    private ReportDAO reportDAO = new ReportDAO();

    public void generateVisitsReport() {
        reportDAO.generateVisitsReport();
    }
}