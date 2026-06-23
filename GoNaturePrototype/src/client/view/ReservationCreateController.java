package client.view;

import client.app.Session;
import client.service.NetworkService;
import common.dto.ParkDTO;
import common.dto.ReservationDTO;
import common.dto.VisitType;
import common.dto.VisitorDTO;
import javafx.fxml.FXML;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DateCell;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

/**
 * Booking form for a new park reservation (INDIVIDUAL / FAMILY / GROUP visits).
 *
 * <p>Collects park, visitor id, date, optional time, party size and visit type,
 * then sends {@code CREATE_RESERVATION} via {@link NetworkService}. On success it
 * shows the server-issued confirmation code; on failure (e.g. no capacity for the
 * chosen date) it surfaces the server's message.
 *
 * <p>Choosing GROUP reveals a Guide ID field and enforces the group cap of 15
 * client-side; the server re-checks both the guide and the cap, so these UI
 * checks are convenience only.
 *
 * <p>Extends {@link BaseController} for navigation lifecycle parity with the other
 * screens; it does not subscribe to push events (realtime push for reservations
 * is a later session).
 */
public class ReservationCreateController extends BaseController {

    /** Maximum party size for a guide-led group visit (mirrored on the server). */
    private static final int MAX_GROUP_SIZE = 15;

    /**
     * Park dropdown entry: carries the id but renders the name.
     *
     * @param id   the park id sent with the booking
     * @param name the park name shown in the dropdown
     */
    private record ParkOption(int id, String name) {
        @Override public String toString() { return name; }
    }

    /** Park selection dropdown. */
    @FXML private ComboBox<ParkOption> parkCombo;
    /** Visitor-id input (prefilled and locked for a logged-in visitor). */
    @FXML private TextField            visitorField;
    /** Contact email input (notification target). */
    @FXML private TextField            emailField;
    /** Contact phone input. */
    @FXML private TextField            phoneField;
    /** Visit-date picker. */
    @FXML private DatePicker           datePicker;
    /** Toggle for whether a specific visit time is set. */
    @FXML private CheckBox             timeEnabledCheck;
    /** Hour dropdown (1–12). */
    @FXML private ComboBox<Integer>    hourCombo;
    /** Minute dropdown (quarter-hour steps). */
    @FXML private ComboBox<String>     minuteCombo;
    /** AM/PM dropdown. */
    @FXML private ComboBox<String>     ampmCombo;
    /** Row holding the three time dropdowns; disabled as a unit when "set time" is off. */
    @FXML private HBox                 timeRow;
    /** Party-size spinner. */
    @FXML private Spinner<Integer>     partySpinner;
    /** Visit-type dropdown (INDIVIDUAL/FAMILY/GROUP). */
    @FXML private ComboBox<VisitType>  typeCombo;
    /** Row holding the guide-id field, shown only for GROUP visits. */
    @FXML private VBox                 guideRow;
    /** Guide-id input (GROUP visits only). */
    @FXML private TextField            guideField;
    /** Toggle for paying in advance. */
    @FXML private CheckBox             prePayCheck;
    /** Submits the booking. */
    @FXML private Button               bookBtn;
    /** Result/toast label for booking feedback. */
    @FXML private Label                resultLabel;
    /** Server-priced payment confirmation panel. */
    @FXML private VBox                 confirmationBox;

    /** The current client session (used to prefill a logged-in visitor). */
    private final Session session;

    /**
     * Creates the reservation-create controller.
     *
     * @param network the shared network service
     * @param session the current client session
     */
    public ReservationCreateController(NetworkService network, Session session) {
        super(network);
        this.session = session;
    }

    /** FXML lifecycle hook: loads parks, configures inputs, prefills for a visitor. */
    @FXML
    private void initialize() {
        // Park list comes from the server (LIST_PARKS) so the dropdown always
        // reflects the real parks; each option carries the park id used by
        // CREATE_RESERVATION.
        loadParks();

        partySpinner.setValueFactory(
                new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 100, 2));

        // A visit can only be booked for today or a future date, so disable every day
        // before today in the picker — a past date simply can't be chosen. The server
        // re-checks against its own clock, so this is the convenience half of the guard.
        datePicker.setDayCellFactory(picker -> new DateCell() {
            @Override
            public void updateItem(LocalDate date, boolean empty) {
                super.updateItem(date, empty);
                if (date != null && date.isBefore(LocalDate.now())) {
                    setDisable(true);
                }
            }
        });

        // 12-hour clock picker as three plain dropdowns: hour 1–12, minute in
        // quarter-hour steps and a clearly readable AM/PM selector. The trio is
        // disabled until "set time" is ticked; left unticked the booking sends no
        // preferred time (null visitTime), preserving the old blank-field semantics.
        // The 12-hour selection is converted to the 24-hour HH:mm:ss wire format in
        // formatVisitTime().
        hourCombo.getItems().setAll(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12);
        minuteCombo.getItems().setAll("00", "15", "30", "45");
        ampmCombo.getItems().setAll("AM", "PM");
        hourCombo.setValue(9);
        minuteCombo.setValue("00");
        ampmCombo.setValue("AM");
        // Disable the whole time row (the three dropdowns and the colon) as one unit
        // when "set time" is unticked, so the colon mutes together with the dropdowns
        // and the CSS can present a single clean disabled state (.time-row:disabled in
        // client.css). Behaviour is unchanged: unticked still sends null visitTime.
        timeRow.disableProperty().bind(timeEnabledCheck.selectedProperty().not());

        typeCombo.getItems().setAll(VisitType.INDIVIDUAL, VisitType.FAMILY, VisitType.GROUP);
        typeCombo.getSelectionModel().selectFirst();

        // The Guide ID field is only relevant to GROUP visits — reveal it when
        // GROUP is selected, hide it otherwise.
        typeCombo.valueProperty().addListener((obs, oldV, newV) -> showGuideField(newV == VisitType.GROUP));
        showGuideField(typeCombo.getValue() == VisitType.GROUP);

        // A logged-in visitor books for themselves: prefill + lock the id field, and
        // prefill their on-file contact (left editable so they can update it). Staff
        // leave the id blank/editable and the contact empty to enter/confirm per booking.
        if (session.isVisitor()) {
            visitorField.setText(String.valueOf(session.getActorId()));
            visitorField.setEditable(false);

            VisitorDTO me = session.getVisitor();
            if (me != null) {
                if (me.getEmail() != null) emailField.setText(me.getEmail());
                if (me.getPhone() != null) phoneField.setText(me.getPhone());
            }
        }
    }

    /**
     * Shows or hides the guide-id field (relevant only to GROUP visits).
     *
     * @param show whether to reveal the guide field
     */
    private void showGuideField(boolean show) {
        guideRow.setVisible(show);
        guideRow.setManaged(show);
    }

    /**
     * Converts the compact 12-hour picker (hour 1–12, minute, AM/PM) into the
     * 24-hour {@code HH:mm:ss} string the server/DB expect for {@code visit_time}
     * — byte-identical to what the old picker sent. The 12-hour mapping is:
     * 12&nbsp;AM&nbsp;→&nbsp;00, 12&nbsp;PM&nbsp;→&nbsp;12, any other PM hour&nbsp;+&nbsp;12;
     * the minute is zero-padded (e.g. 2:30&nbsp;PM&nbsp;→&nbsp;{@code "14:30:00"}).
     *
     * @return the visit time formatted {@code HH:mm:ss}
     */
    private String formatVisitTime() {
        int     hour12 = hourCombo.getValue();
        boolean pm     = "PM".equals(ampmCombo.getValue());
        int     hour24;
        if (hour12 == 12) {
            hour24 = pm ? 12 : 0;
        } else {
            hour24 = pm ? hour12 + 12 : hour12;
        }
        // minuteCombo values are already zero-padded ("00".."45"), so the result
        // is byte-identical to the old picker's HH:mm:ss wire format.
        return String.format("%02d:%s:00", hour24, minuteCombo.getValue());
    }

    /**
     * Populates the park dropdown from the server via {@code LIST_PARKS}. Each
     * {@link ParkOption} carries the park id (used by {@code CREATE_RESERVATION}),
     * rendering only the name. On failure the dropdown stays empty and a toast
     * explains why, so a booking can't be made against a stale hardcoded id.
     */
    private void loadParks() {
        network.listParks().thenAccept(res -> {
            if (!res.isSuccess()) {
                Widgets.showToast(resultLabel, false, res.getMessage());
                return;
            }
            parkCombo.getItems().clear();
            if (res.getData() instanceof List<?> raw) {
                for (Object o : raw) {
                    ParkDTO p = (ParkDTO) o;
                    parkCombo.getItems().add(new ParkOption(p.getId(), p.getName()));
                }
            }
            parkCombo.getSelectionModel().selectFirst();
        });
    }

    /** Book-button handler: validates the form and sends CREATE_RESERVATION. */
    @FXML
    private void onBook() {
        // Clear any confirmation from a previous booking before validating this one.
        hideConfirmation();

        ParkOption park = parkCombo.getValue();
        if (park == null) {
            Widgets.showToast(resultLabel, false, "Please choose a park");
            return;
        }

        // A logged-in visitor's id is prefilled (and locked) above; staff type
        // the visitor's national ID to book on their behalf.
        String visitorRaw = visitorField.getText() == null ? "" : visitorField.getText().trim();
        long visitorId;
        try {
            visitorId = Long.parseLong(visitorRaw);
        } catch (NumberFormatException ex) {
            Widgets.showToast(resultLabel, false, "Enter a valid numeric Visitor ID");
            return;
        }

        // Email + phone are both required: email is the booking's notification
        // target, phone the fallback contact. The server re-validates both — these
        // guards are convenience for fast inline feedback.
        String email = emailField.getText() == null ? "" : emailField.getText().trim();
        if (!isValidEmail(email)) {
            Widgets.showToast(resultLabel, false, "A valid email is required (e.g. name@example.com)");
            return;
        }
        String phone = phoneField.getText() == null ? "" : phoneField.getText().trim();
        if (!isValidPhone(phone)) {
            Widgets.showToast(resultLabel, false, "Enter a valid phone number (at least 10 digits)");
            return;
        }

        if (datePicker.getValue() == null) {
            Widgets.showToast(resultLabel, false, "Please select a visit date");
            return;
        }
        // Belt-and-braces: the picker already disables past days, but re-check before
        // sending in case a past date was set some other way. The server is the real gate.
        if (datePicker.getValue().isBefore(LocalDate.now())) {
            Widgets.showToast(resultLabel, false, "Reservations must be for today or a future date");
            return;
        }

        // Optional visit time from the clock-style picker: only sent when "set time"
        // is ticked, converted to the 24-hour HH:mm:ss wire format; otherwise null
        // (no preference), exactly like the old blank field.
        String visitTime = null;
        if (timeEnabledCheck.isSelected()) {
            visitTime = formatVisitTime();
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

        // Group visits are guide-led and capped at 15. These checks mirror the
        // server's — the server re-validates, so the UI guard is convenience only.
        Long guideId = null;
        if (visitType == VisitType.GROUP) {
            if (partySize > MAX_GROUP_SIZE) {
                Widgets.showToast(resultLabel, false, "Group size cannot exceed " + MAX_GROUP_SIZE);
                return;
            }
            String guideRaw = guideField.getText() == null ? "" : guideField.getText().trim();
            try {
                guideId = Long.parseLong(guideRaw);
            } catch (NumberFormatException ex) {
                Widgets.showToast(resultLabel, false, "Enter a valid numeric Guide ID for group bookings");
                return;
            }
        }

        String visitDate = datePicker.getValue().format(DateTimeFormatter.ISO_LOCAL_DATE);

        // Whether the visitor opts to pay up front; the server prices accordingly.
        boolean paidInAdvance = prePayCheck.isSelected();

        bookBtn.setText("Booking…");
        bookBtn.setDisable(true);

        // visitTime/guideId are reassigned above, so capture final copies for the
        // async callback (and the waitlist re-send, which reuses the same inputs).
        final String visitTimeFinal = visitTime;
        final Long   guideIdFinal   = guideId;

        network.createReservation(park.id(), visitorId, visitDate, visitTimeFinal, partySize, visitType, guideIdFinal, paidInAdvance, email, phone)
               .thenAccept(res -> {
                    bookBtn.setText("+  Book Visit");
                    bookBtn.setDisable(false);
                    if (!res.isSuccess()) {
                        // Only the "park full" capacity rejection offers the waiting
                        // list; every other failure (bad guide, group cap, …) is just
                        // surfaced as a toast.
                        if (isCapacityFailure(res.getMessage())) {
                            promptJoinWaitlist(park.id(), visitorId, visitDate, visitTimeFinal,
                                    partySize, visitType, guideIdFinal, paidInAdvance, email, phone);
                        } else {
                            Widgets.showToast(resultLabel, false, res.getMessage());
                        }
                        return;
                    }
                    ReservationDTO created = (ReservationDTO) res.getData();
                    Widgets.showToast(resultLabel, true,
                            "Reservation #" + created.getId()
                            + " booked · Confirmation code: " + created.getConfirmationCode());
                    // Show the server-priced payment confirmation. The total comes
                    // straight from the returned DTO — never recomputed client-side.
                    showConfirmation(created);
               });
    }

    /**
     * Whether a failed {@code CREATE_RESERVATION} was rejected specifically for lack
     * of capacity (the only case that offers the waiting list). Matches the server's
     * "No capacity for that date …" message; any other failure is a plain error.
     *
     * @param message the server's failure message
     * @return {@code true} if this was the capacity-full rejection
     */
    private boolean isCapacityFailure(String message) {
        return message != null && message.contains("No capacity");
    }

    /**
     * Basic client-side email check (non-blank, contains an {@code '@'} and a
     * {@code '.'}) mirroring the server's {@code isValidEmail}. A convenience guard
     * for fast inline feedback — the server re-validates, so it is the real gate.
     *
     * @param email the trimmed email entered in the form
     * @return {@code true} if it looks well-formed enough to send
     */
    private boolean isValidEmail(String email) {
        return email != null && email.contains("@") && email.contains(".");
    }

    /**
     * Basic client-side phone check (at least ten digits, ignoring formatting)
     * mirroring the server's {@code isValidPhone}. Convenience guard for fast inline
     * feedback — the server re-validates, so it is the real gate.
     *
     * @param phone the trimmed phone entered in the form
     * @return {@code true} if it contains at least ten digits
     */
    private boolean isValidPhone(String phone) {
        if (phone == null) {
            return false;
        }
        int digits = 0;
        for (int i = 0; i < phone.length(); i++) {
            if (Character.isDigit(phone.charAt(i))) {
                digits++;
            }
        }
        return digits >= 10;
    }

    /**
     * Asks the visitor whether to join the waiting list after a park-full rejection.
     * On confirm, re-sends the <em>same</em> booking inputs as {@code JOIN_WAITLIST}.
     *
     * @param parkId        target park id
     * @param visitorId     the visitor's national id
     * @param visitDate     visit date, ISO {@code yyyy-MM-dd}
     * @param visitTime     visit time {@code HH:mm:ss}, or {@code null}
     * @param partySize     number of people in the party
     * @param visitType     INDIVIDUAL, FAMILY, or GROUP
     * @param guideId       guide id for GROUP visits, or {@code null}
     * @param paidInAdvance whether the visitor opts to pay up front
     * @param email         contact email
     * @param phone         contact phone
     */
    private void promptJoinWaitlist(int parkId, long visitorId, String visitDate, String visitTime,
                                    int partySize, VisitType visitType, Long guideId, boolean paidInAdvance,
                                    String email, String phone) {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                "Park is full for that date — join the waiting list?",
                ButtonType.YES, ButtonType.NO);
        confirm.setHeaderText(null);
        confirm.setTitle("Park Full");
        // Match the app theme: reuse the scene's stylesheet on the dialog pane.
        Scene scene = bookBtn.getScene();
        if (scene != null) confirm.getDialogPane().getStylesheets().addAll(scene.getStylesheets());

        Optional<ButtonType> choice = confirm.showAndWait();
        if (choice.isEmpty() || choice.get() != ButtonType.YES) {
            return;
        }

        bookBtn.setDisable(true);
        network.joinWaitlist(parkId, visitorId, visitDate, visitTime, partySize, visitType, guideId, paidInAdvance, email, phone)
               .thenAccept(res -> {
                    bookBtn.setDisable(false);
                    if (!res.isSuccess()) {
                        Widgets.showToast(resultLabel, false, res.getMessage());
                        return;
                    }
                    Widgets.showToast(resultLabel, true,
                            "You're on the waiting list — your spot will be honored when a slot frees up.");
               });
    }

    /**
     * Renders the payment confirmation for a freshly booked reservation from the
     * server's {@link ReservationDTO}: reservation number, the server-computed
     * total (price in cents formatted as shekels), and whether it was paid in
     * advance or is due on arrival.
     *
     * @param created the reservation returned by the server
     */
    private void showConfirmation(ReservationDTO created) {
        confirmationBox.getChildren().clear();

        Label title = new Label("✓ Payment Confirmation");
        title.getStyleClass().add("payment-title");

        double total = created.getPriceCents() / 100.0;
        String paymentStatus = created.isPaidInAdvance() ? "Paid in advance" : "Due on arrival";

        confirmationBox.getChildren().addAll(
                title,
                paymentRow("Reservation", "#" + created.getId()),
                paymentRow("Total", String.format("₪%.2f", total)),
                paymentRow("Payment", paymentStatus));

        confirmationBox.setVisible(true);
        confirmationBox.setManaged(true);
    }

    /**
     * Builds one key/value line for the payment confirmation panel.
     *
     * @param key   the line label
     * @param value the line value
     * @return the key/value row
     */
    private HBox paymentRow(String key, String value) {
        Label k = new Label(key);
        k.getStyleClass().add("key");
        Label v = new Label(value);
        v.getStyleClass().add("val");
        HBox row = new HBox(k, v);
        row.getStyleClass().add("result-row");
        return row;
    }

    /** Hides and empties the payment confirmation panel. */
    private void hideConfirmation() {
        confirmationBox.getChildren().clear();
        confirmationBox.setVisible(false);
        confirmationBox.setManaged(false);
    }
}
