package server.net;

import common.dto.*;
import server.control.reservation.ReservationController;
import server.control.reservation.CancellationController;
import server.control.entrance.EntryController;
import server.control.entrance.ExitController;
import server.control.reports.ReportController;
import server.dao.ParkDAO;

public class RequestRouter {
    private ReservationController reservationController = new ReservationController();
    private CancellationController cancellationController = new CancellationController();
    private EntryController entryController = new EntryController();
    private ExitController exitController = new ExitController();
    private ReportController reportController = new ReportController();

    public ServerResponse handle(ClientRequest request) {
        try {
            switch (request.getType()) {
                case PING:
                    return new ServerResponse(true, "Server is available.");
                case LIST_PARKS:
                    return new ServerResponse(true, "Parks loaded.", new ParkDAO().findAllParks());
                case CREATE_RESERVATION:
                    ReservationDTO created = reservationController.createReservation(
                            (int) request.get("visitorId"), (int) request.get("parkId"),
                            (String) request.get("date"), (String) request.get("time"),
                            (int) request.get("numberOfVisitors"), (String) request.get("visitorType"));
                    return created == null ? new ServerResponse(false, "Reservation failed: no availability or invalid data.") : new ServerResponse(true, "Reservation created.", created);
                case GET_RESERVATION:
                    ReservationDTO reservation = reservationController.getReservation((int) request.get("reservationId"));
                    return reservation == null ? new ServerResponse(false, "Reservation not found.") : new ServerResponse(true, "Reservation found.", reservation);
                case UPDATE_RESERVATION:
                    boolean updated = reservationController.updateReservation((int) request.get("reservationId"), (String) request.get("date"), (int) request.get("numberOfVisitors"));
                    return new ServerResponse(updated, updated ? "Reservation updated." : "Reservation update failed.");
                case CANCEL_RESERVATION:
                    boolean cancelled = cancellationController.cancelReservation((int) request.get("reservationId"));
                    return new ServerResponse(cancelled, cancelled ? "Reservation cancelled." : "Cancellation failed.");
                case ENTER_PARK:
                    int visitId = entryController.enterPark((int) request.get("reservationId"));
                    return visitId < 0 ? new ServerResponse(false, "Entry denied.") : new ServerResponse(true, "Entry approved. Visit ID: " + visitId);
                case EXIT_PARK:
                    boolean exit = exitController.exitPark((int) request.get("reservationId"));
                    return new ServerResponse(exit, exit ? "Exit recorded." : "Exit failed.");
                case GENERATE_VISITS_REPORT:
                    return new ServerResponse(true, "Visits report generated.", reportController.generateVisitsReport((String) request.get("startDate"), (String) request.get("endDate")));
                case GENERATE_USAGE_REPORT:
                    return new ServerResponse(true, "Usage report generated.", reportController.generateUsageReport((String) request.get("startDate"), (String) request.get("endDate")));
                case GENERATE_CANCELLATIONS_REPORT:
                    return new ServerResponse(true, "Cancellations report generated.", reportController.generateCancellationsReport((String) request.get("startDate"), (String) request.get("endDate")));
                default:
                    return new ServerResponse(false, "Unsupported request.");
            }
        } catch (Exception e) {
            e.printStackTrace();
            return new ServerResponse(false, "Server error: " + e.getMessage());
        }
    }
}
