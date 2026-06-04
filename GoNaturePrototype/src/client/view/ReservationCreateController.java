package client.view;

import client.app.Session;
import client.service.NetworkService;
import common.dto.ReservationDTO;
import common.dto.VisitType;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.control.TextField;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/**
 * Booking form for a new park reservation (INDIVIDUAL / FAMILY visits).
 *
 * <p>Collects park, visitor id, date, optional time, party size and visit type,
 * then sends {@code CREATE_RESERVATION} via {@link NetworkService}. On success it
 * shows the server-issued confirmation code; on failure (e.g. no capacity for the
 * chosen date) it surfaces the server's message.
 *
 * <p>Extends {@link BaseController} for navigation lifecycle parity with the other
 * screens; it does not subscribe to push events (realtime push for reservations
 * is a later session).
 */
public class ReservationCreateController extends BaseController {

    /** Park dropdown entry: carries the id but renders the name. */
    private record ParkOption(int id, String name) {
        @Override public String toString() { return name; }
    }

    @FXML private ComboBox<ParkOption> parkCombo;
    @FXML private TextField            visitorField;
    @FXML private DatePicker           datePicker;
    @FXML private TextField            timeField;
    @FXML private Spinner<Integer>     partySpinner;
    @FXML private ComboBox<VisitType>  typeCombo;
    @FXML private Button               bookBtn;
    @FXML private Label                resultLabel;

    public ReservationCreateController(NetworkService network, Session session) {
        super(network);
    }

    @FXML
    private void initialize() {
        // TODO: load parks dynamically when the Parks feature lands. Until then
        // the dropdown mirrors the two seeded parks from setup.sql.
        parkCombo.getItems().setAll(
                new ParkOption(1, "Galilee Park"),
                new ParkOption(2, "Carmel Park"));
        parkCombo.getSelectionModel().selectFirst();

        partySpinner.setValueFactory(
                new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 100, 2));

        // Only INDIVIDUAL and FAMILY are bookable this session; GROUP arrives later.
        typeCombo.getItems().setAll(VisitType.INDIVIDUAL, VisitType.FAMILY);
        typeCombo.getSelectionModel().selectFirst();
    }

    @FXML
    private void onBook() {
        ParkOption park = parkCombo.getValue();
        if (park == null) {
            Widgets.showToast(resultLabel, false, "Please choose a park");
            return;
        }

        // TODO: replace with logged-in visitor when Auth lands. For now the
        // visitor identifies themselves by typing their national ID.
        String visitorRaw = visitorField.getText() == null ? "" : visitorField.getText().trim();
        long visitorId;
        try {
            visitorId = Long.parseLong(visitorRaw);
        } catch (NumberFormatException ex) {
            Widgets.showToast(resultLabel, false, "Enter a valid numeric Visitor ID");
            return;
        }

        if (datePicker.getValue() == null) {
            Widgets.showToast(resultLabel, false, "Please select a visit date");
            return;
        }

        // TODO: replace the free-text time field with a clock-style time picker
        // (e.g. hour/minute spinners) for choosing the optional visit hour.
        // Optional time: blank → null; otherwise must parse to HH:mm[:ss].
        String visitTime = null;
        String timeRaw = timeField.getText() == null ? "" : timeField.getText().trim();
        if (!timeRaw.isEmpty()) {
            try {
                LocalTime t = LocalTime.parse(timeRaw);
                visitTime = t.format(DateTimeFormatter.ofPattern("HH:mm:ss"));
            } catch (Exception ex) {
                Widgets.showToast(resultLabel, false, "Enter time as HH:mm (e.g. 09:30) or leave blank");
                return;
            }
        }

        // Commit any text typed into the editable spinner before reading it.
        try {
            int typed = Integer.parseInt(partySpinner.getEditor().getText().trim());
            if (typed >= 1 && typed <= 100) partySpinner.getValueFactory().setValue(typed);
        } catch (NumberFormatException ignored) {}
        int partySize = partySpinner.getValue();
        if (partySize < 1) {
            Widgets.showToast(resultLabel, false, "Party size must be at least 1");
            return;
        }

        VisitType visitType = typeCombo.getValue();
        if (visitType == null) {
            Widgets.showToast(resultLabel, false, "Please choose a visit type");
            return;
        }

        String visitDate = datePicker.getValue().format(DateTimeFormatter.ISO_LOCAL_DATE);

        bookBtn.setText("Booking…");
        bookBtn.setDisable(true);

        network.createReservation(park.id(), visitorId, visitDate, visitTime, partySize, visitType)
               .thenAccept(res -> {
                    bookBtn.setText("+  Book Visit");
                    bookBtn.setDisable(false);
                    if (!res.isSuccess()) {
                        Widgets.showToast(resultLabel, false, res.getMessage());
                        return;
                    }
                    ReservationDTO created = (ReservationDTO) res.getData();
                    Widgets.showToast(resultLabel, true,
                            "Reservation #" + created.getId()
                            + " booked · Confirmation code: " + created.getConfirmationCode());
               });
    }
}
