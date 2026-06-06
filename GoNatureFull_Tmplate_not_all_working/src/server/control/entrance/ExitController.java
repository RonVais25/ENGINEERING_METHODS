package server.control.entrance;

import server.dao.VisitDAO;

public class ExitController {
    public boolean exitPark(int reservationId) {
        return new VisitDAO().registerExit(reservationId);
    }
}
