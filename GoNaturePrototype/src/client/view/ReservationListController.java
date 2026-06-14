package client.view;

import client.app.Session;
import client.service.NetworkService;
import common.dto.ReservationDTO;
import common.dto.ReservationStatus;
import common.dto.ReservationUpdateResultDTO;
import common.dto.ServerEvent;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * "My Reservations" screen: look up a visitor's reservations by id and manage
 * them. Each row offers Confirm / Cancel, enabled only for statuses where the
 * action is legal. The disabling is a UX hint — the server independently
 * enforces the legal transitions, so a stale or tampered client cannot drive an
 * illegal one.
 *
 * <p>Realtime push: after each successful load the screen subscribes (through the
 * {@link BaseController#subscribe} helper) to every reservation id on screen, so a
 * confirm/cancel/update committed by another client refreshes the list within ~1s.
 * The push handler only re-reads the list (never a mutating op), so applying a
 * pushed change cannot loop back into another publish. {@link BaseController#onHide()}
 * auto-unsubscribes when the screen is navigated away.
 */
public class ReservationListController extends BaseController {

    @FXML private TextField visitorField;
    @FXML private Button    loadBtn;
    @FXML private Label     resultLabel;
    @FXML private Label     cardHeaderLabel;
    @FXML private VBox      tableBox;

    // The visitor whose list is currently shown, so action handlers can refresh
    // the same list after a successful confirm/cancel. -1 means "nothing loaded".
    private long currentVisitorId = -1;

    private final Session session;

    public ReservationListController(NetworkService network, Session session) {
        super(network);
        this.session = session;
    }

    @FXML
    private void initialize() {
        visitorField.setOnAction(e -> onLoad());

        // A logged-in visitor only sees their own reservations: prefill + lock
        // the id field and load it straight away. Staff leave it editable so they
        // can look up any visitor's list.
        if (session.isVisitor()) {
            visitorField.setText(String.valueOf(session.getActorId()));
            visitorField.setEditable(false);
            loadFor(session.getActorId());
        }
    }

    @FXML
    private void onLoad() {
        // Visitor's id is prefilled (and locked) above; staff type a visitor id here.
        String raw = visitorField.getText() == null ? "" : visitorField.getText().trim();
        long visitorId;
        try {
            visitorId = Long.parseLong(raw);
        } catch (NumberFormatException ex) {
            Widgets.showToast(resultLabel, false, "Enter a valid numeric Visitor ID");
            return;
        }
        loadFor(visitorId);
    }

    private void loadFor(long visitorId) {
        currentVisitorId = visitorId;
        loadBtn.setDisable(true);
        network.listReservations(visitorId).thenAccept(res -> {
            loadBtn.setDisable(false);
            if (!res.isSuccess()) {
                Widgets.showToast(resultLabel, false, res.getMessage());
                return;
            }
            List<ReservationDTO> rows = new ArrayList<>();
            if (res.getData() instanceof List<?> raw) {
                for (Object o : raw) rows.add((ReservationDTO) o);
            }
            populate(rows);
            resubscribe(rows);
        });
    }

    private void populate(List<ReservationDTO> rows) {
        cardHeaderLabel.setText("RESERVATIONS — VISITOR " + currentVisitorId);
        tableBox.getChildren().setAll(headerRow());

        if (rows.isEmpty()) {
            Label none = new Label("No reservations for this visitor.");
            none.getStyleClass().addAll("history-cell", "muted");
            HBox row = new HBox(none);
            row.getStyleClass().add("history-row");
            tableBox.getChildren().add(row);
            return;
        }

        for (int i = 0; i < rows.size(); i++) {
            tableBox.getChildren().add(dataRow(rows.get(i), i < rows.size() - 1));
        }
    }

    /**
     * Points this screen's realtime subscriptions at exactly the reservation ids
     * currently displayed. Drops the previous set first via the
     * {@link BaseController#unsubscribeAll()} helper (which also sends the matching
     * UNSUBSCRIBE), so a fresh Load or a push-driven refresh never leaks
     * subscriptions; {@link BaseController#onHide()} drops the rest on navigation.
     */
    private void resubscribe(List<ReservationDTO> rows) {
        unsubscribeAll();
        for (ReservationDTO r : rows) {
            subscribe("reservation", r.getId(), this::onReservationEvent);
        }
    }

    /**
     * Reacts to a pushed reservation change. Runs on the FX thread (the
     * {@code EventBus} marshals via {@code Platform.runLater}). It only re-reads
     * the visitor's list (LIST_RESERVATIONS is read-only and publishes nothing),
     * so applying a pushed change can never re-trigger a publish and loop.
     *
     * @param ev the pushed event (its id is one of the displayed reservations)
     */
    private void onReservationEvent(ServerEvent ev) {
        if (currentVisitorId >= 0) loadFor(currentVisitorId);
    }

    private HBox headerRow() {
        HBox row = new HBox();
        row.getStyleClass().add("history-header-row");
        row.getChildren().addAll(
                headerCell("ID",      60),
                headerCell("DATE",   110),
                headerCell("PARTY",   70),
                headerCell("TYPE",   110),
                headerCell("STATUS", 110),
                flexSpacer(),
                headerCell("ACTIONS", 0));
        return row;
    }

    /** A zero-width filler that absorbs the row's slack so fixed columns and the
     *  action buttons keep their natural widths instead of being squeezed. */
    private Region flexSpacer() {
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        return spacer;
    }

    private HBox dataRow(ReservationDTO r, boolean withDivider) {
        Label idLbl    = cell("#" + r.getId(),                "num", 60);
        Label dateLbl  = cell(r.getVisitDate(),               null, 110);
        Label partyLbl = cell(String.valueOf(r.getPartySize()), null, 70);
        Label typeLbl  = cell(r.getVisitType().name(),        null, 110);

        Label statusTag = new Label(r.getStatus().name());
        statusTag.getStyleClass().addAll("status-tag", r.getStatus().name().toLowerCase());
        statusTag.setPrefWidth(110);

        Button confirmBtn = new Button("Confirm");
        confirmBtn.getStyleClass().add("btn-secondary");
        confirmBtn.setDisable(r.getStatus() != ReservationStatus.PENDING);
        confirmBtn.setOnAction(e -> confirm(r.getId()));

        Button cancelBtn = new Button("Cancel");
        cancelBtn.getStyleClass().add("btn-secondary");
        cancelBtn.setDisable(!canCancel(r.getStatus()));
        cancelBtn.setOnAction(e -> cancel(r.getId()));

        // Edit (reschedule date / time / party) is only legal for an active
        // booking, mirroring the server's PENDING/CONFIRMED guard; disabled
        // otherwise. The server re-validates, so this is a UX hint only.
        Button editBtn = new Button("Edit");
        editBtn.getStyleClass().add("btn-secondary");
        editBtn.setDisable(!canEdit(r.getStatus()));
        editBtn.setOnAction(e -> edit(r));

        // Pin each button to its preferred (label) width so a tight row never
        // shrinks them into ellipsized stubs ("Ac.." / "Ca.." / "E..").
        for (Button b : new Button[] { confirmBtn, cancelBtn, editBtn }) {
            b.setMinWidth(Region.USE_PREF_SIZE);
        }

        HBox actions = new HBox(8, confirmBtn, cancelBtn, editBtn);
        actions.setAlignment(Pos.CENTER_LEFT);

        // TODO: make the row clickable to open a read-only detail view (GET_RESERVATION
        // by id) showing the full reservation — time, price, confirmation code, guide,
        // waitlist position. Right now a visitor can list rows but can't drill into one.
        //
        // A flexible spacer takes the row's slack so the actions keep their natural
        // width on the right; when space is tight the spacer collapses first (and
        // then the info cells), never the buttons. The header row carries a matching
        // spacer so the ACTIONS column header stays aligned over the buttons.
        HBox row = new HBox(idLbl, dateLbl, partyLbl, typeLbl, statusTag, flexSpacer(), actions);
        row.getStyleClass().add("history-row");
        if (withDivider) row.getStyleClass().add("with-divider");
        return row;
    }

    /** Cancel is legal from PENDING, CONFIRMED or WAITING (mirrors the server rule). */
    private boolean canCancel(ReservationStatus s) {
        return s == ReservationStatus.PENDING
                || s == ReservationStatus.CONFIRMED
                || s == ReservationStatus.WAITING;
    }

    /** Edit (reschedule) is legal only from PENDING or CONFIRMED (mirrors the server rule). */
    private boolean canEdit(ReservationStatus s) {
        return s == ReservationStatus.PENDING || s == ReservationStatus.CONFIRMED;
    }

    private void confirm(int reservationId) {
        network.confirmReservation(reservationId).thenAccept(res -> {
            Widgets.showToast(resultLabel, res.isSuccess(), res.getMessage());
            if (res.isSuccess()) reload();
        });
    }

    private void cancel(int reservationId) {
        network.cancelReservation(reservationId).thenAccept(res -> {
            Widgets.showToast(resultLabel, res.isSuccess(), res.getMessage());
            if (res.isSuccess()) reload();
        });
    }

    /**
     * Opens the inline edit dialog for one reservation: a small modal prefilled
     * with the current date (DatePicker), optional time and party size (Spinner).
     * On Apply it sends {@code UPDATE_RESERVATION}; the server re-checks the status,
     * group cap and capacity and recomputes the price. On failure the server's
     * message is surfaced; on success any price change is settled (see
     * {@link #showSettlement}) and the list is refreshed.
     *
     * <p>This is user-initiated only. A pushed update never lands here — the push
     * handler ({@link #onReservationEvent}) only re-reads the list — so applying an
     * edit cannot loop back into another update.
     */
    private void edit(ReservationDTO r) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Edit Reservation #" + r.getId());
        dialog.setHeaderText("Reschedule date, time or party size.");

        ButtonType applyType = new ButtonType("Apply", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(applyType, ButtonType.CANCEL);

        DatePicker datePicker = new DatePicker();
        try {
            datePicker.setValue(LocalDate.parse(r.getVisitDate()));
        } catch (Exception ignored) {
            // leave the picker empty if the stored date can't be parsed
        }

        TextField timeField = new TextField(r.getVisitTime() == null ? "" : r.getVisitTime());
        timeField.setPromptText("HH:mm (optional)");

        Spinner<Integer> partySpinner = new Spinner<>();
        partySpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(
                1, 100, Math.max(1, r.getPartySize())));
        partySpinner.setEditable(true);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.add(new Label("Date"),  0, 0);
        grid.add(datePicker,         1, 0);
        grid.add(new Label("Time"),  0, 1);
        grid.add(timeField,          1, 1);
        grid.add(new Label("Party"), 0, 2);
        grid.add(partySpinner,       1, 2);
        dialog.getDialogPane().setContent(grid);

        // Match the app theme: reuse the scene's stylesheet on the dialog pane.
        Scene scene = tableBox.getScene();
        if (scene != null) dialog.getDialogPane().getStylesheets().addAll(scene.getStylesheets());

        Optional<ButtonType> choice = dialog.showAndWait();
        if (choice.isEmpty() || choice.get() != applyType) {
            return;
        }

        // Validate: a date is required.
        if (datePicker.getValue() == null) {
            Widgets.showToast(resultLabel, false, "Please choose a visit date");
            return;
        }
        String visitDate = datePicker.getValue().format(DateTimeFormatter.ISO_LOCAL_DATE);

        // Optional time: blank → null; otherwise must parse to HH:mm[:ss].
        String visitTime = null;
        String timeRaw = timeField.getText() == null ? "" : timeField.getText().trim();
        if (!timeRaw.isEmpty()) {
            try {
                visitTime = LocalTime.parse(timeRaw).format(DateTimeFormatter.ofPattern("HH:mm:ss"));
            } catch (Exception ex) {
                Widgets.showToast(resultLabel, false, "Enter time as HH:mm (e.g. 09:30) or leave blank");
                return;
            }
        }

        // Commit any text typed into the editable spinner before reading it.
        try {
            int typed = Integer.parseInt(partySpinner.getEditor().getText().trim());
            if (typed >= 1 && typed <= 100) partySpinner.getValueFactory().setValue(typed);
        } catch (NumberFormatException ignored) {
            // keep the spinner's last committed value
        }
        int partySize = partySpinner.getValue();
        if (partySize < 1) {
            Widgets.showToast(resultLabel, false, "Party size must be at least 1");
            return;
        }

        network.updateReservation(r.getId(), visitDate, visitTime, partySize).thenAccept(res -> {
            if (!res.isSuccess()) {
                // Capacity / status rejection — surface the server's message.
                Widgets.showToast(resultLabel, false, res.getMessage());
                return;
            }
            Widgets.showToast(resultLabel, true, res.getMessage());
            // Settle any price change (collect / refund / quote at the gate). A
            // zero delta (e.g. only the date changed) settles quietly.
            if (res.getData() instanceof ReservationUpdateResultDTO result) {
                showSettlement(result);
            }
            reload();
        });
    }

    /**
     * Shows the (simulated) settlement for a price change after an edit. No real
     * gateway is involved — this only tells the operator what to do:
     * <ul>
     *   <li>prepaid, price up → collect the difference;</li>
     *   <li>prepaid, price down → refund the difference;</li>
     *   <li>pay-on-arrival → quote the new total due at the gate.</li>
     * </ul>
     * A zero delta (only the date/time changed) is settled quietly — no dialog.
     *
     * @param result the server's update result carrying the old and new price
     */
    private void showSettlement(ReservationUpdateResultDTO result) {
        int delta = result.getDeltaCents();
        if (delta == 0) {
            return; // nothing to settle (e.g. only the date changed)
        }

        ReservationDTO r = result.getReservation();
        double newTotal = result.getNewPriceCents() / 100.0;
        double diff     = Math.abs(delta) / 100.0;

        String msg;
        if (!r.isPaidInAdvance()) {
            msg = String.format("New total ₪%.2f, due at the gate.", newTotal);
        } else if (delta > 0) {
            msg = String.format("Price increased to ₪%.2f. Collect ₪%.2f difference. (Simulation)",
                    newTotal, diff);
        } else {
            msg = String.format("Price decreased to ₪%.2f. Refund ₪%.2f. (Simulation)",
                    newTotal, diff);
        }

        Alert info = new Alert(Alert.AlertType.INFORMATION, msg, ButtonType.OK);
        info.setTitle("Settlement");
        info.setHeaderText("Settlement (Simulation)");
        Scene scene = tableBox.getScene();
        if (scene != null) info.getDialogPane().getStylesheets().addAll(scene.getStylesheets());
        info.showAndWait();
    }

    private void reload() {
        if (currentVisitorId >= 0) loadFor(currentVisitorId);
    }

    private Label headerCell(String text, double w) {
        Label l = new Label(text);
        l.getStyleClass().add("history-header-cell");
        if (w > 0) l.setPrefWidth(w);
        return l;
    }

    private Label cell(String text, String modifier, double w) {
        Label l = new Label(text);
        l.getStyleClass().add("history-cell");
        if (modifier != null) l.getStyleClass().add(modifier);
        l.setPrefWidth(w);
        return l;
    }
}
