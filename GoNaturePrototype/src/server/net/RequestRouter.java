package server.net;

import common.dto.ClientRequest;
import common.dto.ReservationDTO;
import common.dto.ServerResponse;
import common.model.Reservation;
import common.model.VisitRecord;
import server.control.EntryController;
import server.control.ReportController;
import server.control.ReservationController;

public class RequestRouter {

    private ReservationController reservationController = new ReservationController();
    private EntryController entryController = new EntryController();
    private ReportController reportController = new ReportController();

    public ServerResponse handle(ClientRequest request) {

        switch (request.getType()) {

            case CREATE_RESERVATION:
                String parkName = (String) request.get("parkName");
                String date = (String) request.get("date");
                String time = (String) request.get("time");
                int visitorsCount = (int) request.get("visitorsCount");

                Reservation reservation = reservationController.createReservation(
                        parkName,
                        date,
                        time,
                        visitorsCount
                );

                if (reservation != null) {
                    return new ServerResponse(
                            true,
                            "Reservation created successfully. ID: " + reservation.getReservationId()
                    );
                }

                return new ServerResponse(false, "Reservation failed.");

            case GET_RESERVATION:
                String getId = (String) request.get("reservationId");

                ReservationDTO dto = reservationController.getReservationFromDB(getId);

                if (dto != null) {
                    return new ServerResponse(true, "Reservation found.", dto);
                }

                return new ServerResponse(false, "Reservation not found.");

            case UPDATE_RESERVATION:
                String updateId = (String) request.get("reservationId");
                String newDate = (String) request.get("newDate");
                int newVisitors = (int) request.get("newVisitors");

                boolean updated = reservationController.updateReservationInDB(
                        updateId,
                        newDate,
                        newVisitors
                );

                if (updated) {
                    return new ServerResponse(true, "Reservation updated successfully.");
                }

                return new ServerResponse(false, "Reservation update failed.");

            case ENTER_PARK:
                String enterReservationId = (String) request.get("reservationId");

                VisitRecord visit = entryController.enterPark(enterReservationId);

                if (visit != null) {
                    return new ServerResponse(true, "Entry approved for reservation: " + enterReservationId);
                }

                return new ServerResponse(false, "Entry denied.");

            case EXIT_PARK:
                String exitReservationId = (String) request.get("reservationId");

                boolean exitResult = entryController.exitPark(exitReservationId);

                if (exitResult) {
                    return new ServerResponse(true, "Exit recorded for reservation: " + exitReservationId);
                }

                return new ServerResponse(false, "Exit failed.");

            case GENERATE_VISITS_REPORT:
                reportController.generateVisitsReport();
                return new ServerResponse(true, "Visits report generated on server console.");

            default:
                return new ServerResponse(false, "Unknown request type.");
        }
    }
}