package client.view;

import client.app.Session;
import client.service.NetworkService;
import common.dto.ParkDTO;
import common.dto.ReservationDTO;
import common.dto.ReservationStatus;
import common.dto.ReservationUpdateResultDTO;
import common.dto.ServerEvent;
import common.dto.ServerResponse;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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

    @FXML private VBox      screenRoot;
    @FXML private TextField visitorField;
    @FXML private Button    loadBtn;
    @FXML private Label     resultLabel;
    @FXML private Label     cardHeaderLabel;
    @FXML private VBox      tableBox;

    // The visitor whose list is currently shown, so action handlers can refresh
    // the same list after a successful confirm/cancel. -1 means "nothing loaded".
    private long currentVisitorId = -1;

    // Park id -> name, fetched once on init via LIST_PARKS so rows and the detail
    // show the park's name rather than a bare numeric id; empty until it resolves
    // (rows then fall back to "Park #<id>").
    private Map<Integer, String> parkNames = new HashMap<>();

    private final Session session;

    public ReservationListController(NetworkService network, Session session) {
        super(network);
        this.session = session;
    }

    @FXML
    private void initialize() {
        visitorField.setOnAction(e -> onLoad());

        // A logged-in visitor only sees their own reservations: prefill + lock the
        // id field. Staff leave it editable so they can look up any visitor's list.
        if (session.isVisitor()) {
            visitorField.setText(String.valueOf(session.getActorId()));
            visitorField.setEditable(false);
        }

        // Resolve park id -> name once (the same LIST_PARKS lookup the Dashboard
        // uses) so rows and the detail show the park's name instead of a bare id.
        // Best-effort: on failure parkNames stays empty and the UI falls back to
        // "Park #<id>". The visitor auto-load runs after the names resolve so the
        // first paint already carries them — the future always completes (even when
        // disconnected), so the auto-load is never skipped.
        network.listParks().thenAccept(res -> {
            parkNames = parkNamesFrom(res);
            if (session.isVisitor()) loadFor(session.getActorId());
        });
    }

    /** Builds the park id -> name lookup from a LIST_PARKS response (defensive,
     *  never throws); mirrors the Dashboard's {@code parkNamesFrom}. */
    private Map<Integer, String> parkNamesFrom(ServerResponse res) {
        Map<Integer, String> names = new HashMap<>();
        if (res.isSuccess() && res.getData() instanceof List<?> raw) {
            for (Object o : raw) {
                if (o instanceof ParkDTO p) names.put(p.getId(), p.getName());
            }
        }
        return names;
    }

    /** The park's name, or a "Park #<id>" fallback when the names aren't loaded. */
    private String parkName(int parkId) {
        return parkNames.getOrDefault(parkId, "Park #" + parkId);
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
        // Widths are kept tight (they sum to roughly the original 5-column budget)
        // so the data row — which also carries three pinned action buttons — fits
        // the default window width without squeezing the cells into ellipsized
        // stubs. The header has no buttons, so if the data row squeezed, the two
        // would drift out of alignment.
        row.getChildren().addAll(
                headerCell("ID",     30),
                headerCell("PARK",   92),
                headerCell("DATE",   78),
                headerCell("PARTY",  38),
                headerCell("TYPE",   76),
                headerCell("CODE",   42),
                headerCell("STATUS", 94),
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
        Label idLbl    = cell("#" + r.getId(),                  "num", 30);
        Label parkLbl  = cell(parkName(r.getParkId()),          null, 92);
        Label dateLbl  = cell(r.getVisitDate(),                 null, 78);
        Label partyLbl = cell(String.valueOf(r.getPartySize()), null, 38);
        Label typeLbl  = cell(r.getVisitType().name(),          null, 76);
        Label codeLbl  = cell(r.getConfirmationCode() == null
                ? "—" : String.valueOf(r.getConfirmationCode()), null, 42);

        Label statusTag = new Label(r.getStatus().name());
        statusTag.getStyleClass().addAll("status-tag", r.getStatus().name().toLowerCase());
        statusTag.setPrefWidth(94);

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

        // A flexible spacer takes the row's slack so the actions keep their natural
        // width on the right; when space is tight the spacer collapses first (and
        // then the info cells), never the buttons. The header row carries a matching
        // spacer so the ACTIONS column header stays aligned over the buttons.
        HBox row = new HBox(idLbl, parkLbl, dateLbl, partyLbl, typeLbl, codeLbl, statusTag, flexSpacer(), actions);
        row.getStyleClass().add("history-row");
        if (withDivider) row.getStyleClass().add("with-divider");

        // Clicking a row opens a read-only detail of the full reservation. The
        // action buttons keep their own clicks (Confirm/Cancel/Edit) — a click that
        // lands on (or inside) a Button is ignored here so it doesn't also open the
        // detail. Everything shown comes from the DTO already in hand, so no extra
        // server round-trip and no edit controls.
        row.setCursor(Cursor.HAND);
        row.setOnMouseClicked(e -> {
            if (!clickHitButton(e.getTarget())) showDetail(r);
        });
        return row;
    }

    /** True if the click landed on a Button (or a node inside one), so the row's
     *  click handler should defer to that button's own action instead of opening
     *  the detail view. */
    private boolean clickHitButton(Object target) {
        Node n = (target instanceof Node) ? (Node) target : null;
        while (n != null) {
            if (n instanceof Button) return true;
            n = n.getParent();
        }
        return false;
    }

    /**
     * Opens a small read-only detail of one reservation: every field the row hints
     * at, spelled out — date, time, party, type, status, price, payment, guide and
     * confirmation code. View-only by design: a single Close button and no editable
     * controls (rescheduling stays on the Edit action). All values come from the
     * {@link ReservationDTO} already loaded, so opening a detail never calls the
     * server.
     *
     * @param r the reservation whose row was clicked
     */
    private void showDetail(ReservationDTO r) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Reservation #" + r.getId());
        dialog.setHeaderText("Reservation details (read-only)");
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);

        GridPane grid = new GridPane();
        grid.setHgap(16);
        grid.setVgap(8);

        int row = 0;
        addDetail(grid, row++, "Reservation",       "#" + r.getId());
        addDetail(grid, row++, "Park",              parkName(r.getParkId()));
        addDetail(grid, row++, "Visitor",           String.valueOf(r.getVisitorId()));
        addDetail(grid, row++, "Visit date",        orDash(r.getVisitDate()));
        addDetail(grid, row++, "Visit time",        orDash(r.getVisitTime()));
        addDetail(grid, row++, "Party size",        String.valueOf(r.getPartySize()));
        addDetail(grid, row++, "Visit type",        r.getVisitType().name());
        addDetail(grid, row++, "Status",            r.getStatus().name());
        addDetail(grid, row++, "Price",             String.format("₪%.2f", r.getPriceCents() / 100.0));
        addDetail(grid, row++, "Payment",           r.isPaidInAdvance() ? "Paid in advance" : "Due on arrival");
        addDetail(grid, row++, "Guide",             r.getGuideId() == null ? "—" : String.valueOf(r.getGuideId()));
        addDetail(grid, row++, "Confirmation code", r.getConfirmationCode() == null
                ? "—" : String.valueOf(r.getConfirmationCode()));
        addDetail(grid, row++, "Created",           orDash(r.getCreatedAt()));

        dialog.getDialogPane().setContent(grid);

        // Match the app theme: reuse the scene's stylesheet on the dialog pane.
        Scene scene = tableBox.getScene();
        if (scene != null) dialog.getDialogPane().getStylesheets().addAll(scene.getStylesheets());

        dialog.showAndWait();
    }

    /** Adds one read-only "key: value" line to the detail grid. */
    private void addDetail(GridPane grid, int row, String key, String value) {
        Label k = new Label(key);
        k.getStyleClass().add("key");
        Label v = new Label(value);
        v.getStyleClass().add("val");
        grid.add(k, 0, row);
        grid.add(v, 1, row);
    }

    /** Renders a blank/null field as an em dash so empty values read cleanly. */
    private String orDash(String value) {
        return (value == null || value.isBlank()) ? "—" : value;
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
        if (!confirmAction("Confirm reservation #" + reservationId + "?",
                "Are you sure you want to confirm this reservation?")) {
            return;
        }
        network.confirmReservation(reservationId).thenAccept(res -> {
            Widgets.showToast(resultLabel, res.isSuccess(), res.getMessage());
            if (res.isSuccess()) reload();
        });
    }

    private void cancel(int reservationId) {
        if (!confirmAction("Cancel reservation #" + reservationId + "?",
                "Are you sure you want to cancel this reservation? This cannot be undone.")) {
            return;
        }
        network.cancelReservation(reservationId).thenAccept(res -> {
            Widgets.showToast(resultLabel, res.isSuccess(), res.getMessage());
            if (res.isSuccess()) reload();
        });
    }

    /**
     * Shows a blocking Yes/No confirmation and returns {@code true} only if the
     * user clicked Yes. Both Confirm and Cancel are guarded by this so a stray
     * click never fires the op without the user agreeing first. Yes/No (rather than
     * OK/Cancel) avoids a confusing "Cancel" button on the cancel-reservation prompt.
     *
     * @param header  the bold dialog header (the question)
     * @param content the explanatory body line
     * @return whether the user confirmed
     */
    private boolean confirmAction(String header, String content) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, content, ButtonType.YES, ButtonType.NO);
        alert.setTitle("Please confirm");
        alert.setHeaderText(header);
        // Match the app theme on the dialog, same as the read-only detail dialog.
        Scene scene = tableBox.getScene();
        if (scene != null) alert.getDialogPane().getStylesheets().addAll(scene.getStylesheets());
        Optional<ButtonType> result = alert.showAndWait();
        return result.isPresent() && result.get() == ButtonType.YES;
    }

    /**
     * Launches the on-screen edit flow for one reservation, replacing the old popup
     * dialog: a three-step wizard — Edit Details → Review → Settlement — with a step
     * indicator, rendered in place of the list. Behaviour is unchanged from the
     * dialog: a date / optional-time / party edit, then {@code UPDATE_RESERVATION}
     * (the server re-checks status, group cap and capacity and recomputes the
     * price), the collect/refund/quote settlement, and a live list refresh — only
     * the presentation is richer.
     *
     * <p>User-initiated only. A pushed update never lands here — the push handler
     * ({@link #onReservationEvent}) only re-reads the list — so applying an edit
     * cannot loop back into another update.
     */
    private void edit(ReservationDTO r) {
        new EditFlow(r).start();
    }

    /**
     * Builds the (simulated) settlement line for a price change after an edit. No
     * real gateway is involved — it only tells the operator what to do: prepaid +
     * price up → collect the difference; prepaid + price down → refund; pay-on-
     * arrival → quote the new total due at the gate. Returns {@code null} when the
     * price didn't change (e.g. only the date moved) so the caller can say so.
     *
     * @param result the server's update result carrying the old and new price
     * @return the settlement message, or {@code null} for a zero delta
     */
    private String settlementMessage(ReservationUpdateResultDTO result) {
        int delta = result.getDeltaCents();
        if (delta == 0) {
            return null; // nothing to settle (e.g. only the date changed)
        }
        ReservationDTO r = result.getReservation();
        double newTotal = result.getNewPriceCents() / 100.0;
        double diff     = Math.abs(delta) / 100.0;
        if (!r.isPaidInAdvance()) {
            return String.format("New total ₪%.2f, due at the gate.", newTotal);
        } else if (delta > 0) {
            return String.format("Price increased to ₪%.2f. Collect ₪%.2f difference. (Simulation)", newTotal, diff);
        } else {
            return String.format("Price decreased to ₪%.2f. Refund ₪%.2f. (Simulation)", newTotal, diff);
        }
    }

    /**
     * Builds the step indicator for the edit wizard, mirroring the old Update-Order
     * wizard: a row of numbered circles + captions joined by connector lines, with
     * completed steps ticked and the active one highlighted. Styled by the
     * {@code step-*} classes in client.css.
     *
     * @param active the zero-based index of the current step
     * @param labels the per-step captions (their count drives the number of dots)
     */
    private HBox buildStepIndicator(int active, String[] labels) {
        HBox row = new HBox();
        row.getStyleClass().add("step-row");
        for (int i = 0; i < labels.length; i++) {
            Label num = new Label(i < active ? "✓" : String.valueOf(i + 1));
            num.getStyleClass().add("step-num");

            VBox circle = new VBox(num);
            circle.getStyleClass().add("step-circle");
            circle.setAlignment(Pos.CENTER);
            if (i < active)       circle.getStyleClass().add("done");
            else if (i == active) circle.getStyleClass().add("active");

            Label lbl = new Label(labels[i]);
            lbl.getStyleClass().add("step-label");

            HBox piece = new HBox(8, circle, lbl);
            piece.getStyleClass().add("step-piece");
            if (i == active) piece.getStyleClass().add("active");
            piece.setAlignment(Pos.CENTER_LEFT);
            row.getChildren().add(piece);

            if (i < labels.length - 1) {
                Region line = new Region();
                line.getStyleClass().add("step-line");
                if (i < active) line.getStyleClass().add("done");
                row.getChildren().add(line);
            }
        }
        return row;
    }

    /**
     * The on-screen reschedule wizard for a single reservation. It takes over the
     * screen (saving the list cards and restoring them on exit) and walks the user
     * through Edit Details → Review → Settlement with a step indicator. On confirm
     * it sends the same {@code UPDATE_RESERVATION} the old dialog did, shows the
     * settlement inline, and live-refreshes the list. State for the in-progress edit
     * lives on the instance so it survives the Back/Next step swaps.
     */
    private final class EditFlow {
        private static final String[] STEPS = {"Edit Details", "Review", "Settlement"};

        private final ReservationDTO original;
        private final List<Node> savedView;   // the list cards, restored on exit
        private final VBox card;              // the wizard card swapped in

        // Step-1 inputs — created once so their values survive the step swaps.
        private final DatePicker     datePicker    = new DatePicker();
        private final CheckBox       timeCheck     = new CheckBox("Set a time");
        private final ComboBox<Integer> hourCombo   = new ComboBox<>();
        private final ComboBox<String>  minuteCombo = new ComboBox<>();
        private final ComboBox<String>  ampmCombo   = new ComboBox<>();
        private final Spinner<Integer> partySpinner  = new Spinner<>();
        private final Label          errorLbl      = new Label();

        // Captured on Review and sent on Confirm; result fills the Settlement step.
        private String visitDate;
        private String visitTime;
        private int    partySize;
        private ReservationUpdateResultDTO result;

        EditFlow(ReservationDTO r) {
            this.original   = r;
            this.savedView  = new ArrayList<>(screenRoot.getChildren());
            this.card       = new VBox();
            card.getStyleClass().add("card");
            card.setSpacing(16);
            buildInputs();
        }

        /** Prefills + configures the step-1 controls from the reservation. */
        private void buildInputs() {
            datePicker.getStyleClass().add("date-picker");
            datePicker.setMaxWidth(Double.MAX_VALUE);
            try {
                datePicker.setValue(LocalDate.parse(original.getVisitDate()));
            } catch (Exception ignored) { /* leave empty if unparseable */ }

            // 12-hour picker as three plain dropdowns, mirroring the Book Visit form:
            // Hour 1–12, Minute in quarter-hour steps and a clearly readable AM/PM
            // selector, all disabled until "Set a time" is ticked.
            hourCombo.getItems().setAll(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12);
            minuteCombo.getItems().setAll("00", "15", "30", "45");
            ampmCombo.getItems().setAll("AM", "PM");
            hourCombo.setValue(9);
            minuteCombo.setValue("00");
            ampmCombo.setValue("AM");
            hourCombo.disableProperty().bind(timeCheck.selectedProperty().not());
            minuteCombo.disableProperty().bind(timeCheck.selectedProperty().not());
            ampmCombo.disableProperty().bind(timeCheck.selectedProperty().not());
            hourCombo.getStyleClass().addAll("input-field", "time-combo");
            minuteCombo.getStyleClass().addAll("input-field", "time-combo");
            ampmCombo.getStyleClass().addAll("input-field", "time-combo");
            // Prefill from the stored time if any; otherwise leave "no preference".
            // The stored visit_time is 24-hour HH:mm[:ss]; map it back into the
            // 12-hour dropdowns (00:xx → 12 AM, 12:xx → 12 PM, 13–23 → 1–11 PM),
            // snapping the minute to the nearest quarter-hour the dropdown offers.
            if (original.getVisitTime() != null && !original.getVisitTime().isBlank()) {
                try {
                    LocalTime t = LocalTime.parse(original.getVisitTime());
                    int h24 = t.getHour();
                    int h12 = h24 % 12;
                    if (h12 == 0) h12 = 12;
                    int min = Math.min(45, ((t.getMinute() + 7) / 15) * 15);
                    hourCombo.setValue(h12);
                    minuteCombo.setValue(String.format("%02d", min));
                    ampmCombo.setValue(h24 < 12 ? "AM" : "PM");
                    timeCheck.setSelected(true);
                } catch (Exception ignored) { /* keep unchecked on parse trouble */ }
            }

            partySpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(
                    1, 100, Math.max(1, original.getPartySize())));
            partySpinner.setEditable(true);
            partySpinner.getStyleClass().add("spinner");
            partySpinner.setMaxWidth(Double.MAX_VALUE);

            errorLbl.getStyleClass().add("login-error");
            errorLbl.setWrapText(true);
        }

        /** Swaps the wizard in for the list and shows the first step. */
        void start() {
            screenRoot.getChildren().setAll(card);
            showStep(0);
        }

        private void showStep(int idx) {
            hideError();
            Label title = new Label("EDIT RESERVATION #" + original.getId());
            title.getStyleClass().add("section-label");
            card.getChildren().setAll(
                    title, buildStepIndicator(idx, STEPS), bodyFor(idx), errorLbl, footerFor(idx));
        }

        private Node bodyFor(int idx) {
            switch (idx) {
                case 1:  return reviewBody();
                case 2:  return settlementBody();
                default: return detailsBody();
            }
        }

        private Node detailsBody() {
            Label colon = new Label(":");
            colon.getStyleClass().add("time-colon");
            HBox timeRow = new HBox(8, timeCheck, hourCombo, colon, minuteCombo, ampmCombo);
            timeRow.setAlignment(Pos.CENTER_LEFT);
            return new VBox(8,
                    fieldLabel("Visit Date"),            datePicker,
                    fieldLabel("Visit Time (optional)"), timeRow,
                    fieldLabel("Party Size"),            partySpinner);
        }

        private Node reviewBody() {
            GridPane g = new GridPane();
            g.setHgap(14);
            g.setVgap(8);
            reviewRow(g, 0, "Date",  original.getVisitDate(),                 visitDate);
            reviewRow(g, 1, "Time",  orDash(original.getVisitTime()),         visitTime == null ? "—" : visitTime);
            reviewRow(g, 2, "Party", String.valueOf(original.getPartySize()), String.valueOf(partySize));
            return new VBox(10, hintLabel("Review your changes, then confirm to apply them."), g);
        }

        private void reviewRow(GridPane g, int row, String key, String oldVal, String newVal) {
            Label k = new Label(key);     k.getStyleClass().add("key");
            Label o = new Label(oldVal);  o.getStyleClass().add("val");
            Label arrow = new Label("→");
            Label n = new Label(newVal);  n.getStyleClass().add("val");
            g.add(k, 0, row);
            g.add(o, 1, row);
            g.add(arrow, 2, row);
            g.add(n, 3, row);
        }

        private Node settlementBody() {
            Label ok = new Label("✓ Reservation #" + original.getId() + " updated.");
            ok.getStyleClass().add("payment-title");
            String settle = (result == null) ? null : settlementMessage(result);
            Label detail = new Label(settle == null ? "No price change." : settle);
            detail.getStyleClass().add("hint-text");
            detail.setWrapText(true);
            return new VBox(10, ok, detail);
        }

        private Node footerFor(int idx) {
            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);
            HBox footer = new HBox(10);
            footer.setAlignment(Pos.CENTER_LEFT);
            if (idx == 0) {
                Button cancel = secondary("Cancel");
                cancel.setOnAction(e -> finish());
                Button next = primary("Review →");
                next.setOnAction(e -> { if (validateAndCapture()) showStep(1); });
                footer.getChildren().addAll(cancel, spacer, next);
            } else if (idx == 1) {
                Button back = secondary("← Back");
                back.setOnAction(e -> showStep(0));
                Button confirm = primary("Confirm changes");
                confirm.setOnAction(e -> submit());
                footer.getChildren().addAll(back, spacer, confirm);
            } else {
                Button done = primary("Done");
                done.setOnAction(e -> finish());
                footer.getChildren().addAll(spacer, done);
            }
            return footer;
        }

        /** Validates step 1 and captures the edited values; same rules as the old
         *  dialog (date required, party ≥ 1, time optional → null). */
        private boolean validateAndCapture() {
            if (datePicker.getValue() == null) {
                showError("Please choose a visit date");
                return false;
            }
            visitDate = datePicker.getValue().format(DateTimeFormatter.ISO_LOCAL_DATE);

            // Commit any text typed into the editable party spinner before reading it.
            try {
                int typed = Integer.parseInt(partySpinner.getEditor().getText().trim());
                if (typed >= 1 && typed <= 100) partySpinner.getValueFactory().setValue(typed);
            } catch (NumberFormatException ignored) { /* keep last committed value */ }
            partySize = partySpinner.getValue();
            if (partySize < 1) {
                showError("Party size must be at least 1");
                return false;
            }

            // Optional time from the three dropdowns, converted to the 24-hour wire
            // format; unticked → null (no preference), exactly like the old field.
            visitTime = timeCheck.isSelected() ? formatVisitTime() : null;
            return true;
        }

        /** Converts the 12-hour dropdowns (Hour 1–12, Minute, AM/PM) into the
         *  24-hour {@code HH:mm:ss} string the server expects — byte-identical to
         *  the Book Visit form: 12 AM → 00, 12 PM → 12, any other PM hour + 12. */
        private String formatVisitTime() {
            int     hour12 = hourCombo.getValue();
            boolean pm     = "PM".equals(ampmCombo.getValue());
            int     hour24;
            if (hour12 == 12) {
                hour24 = pm ? 12 : 0;
            } else {
                hour24 = pm ? hour12 + 12 : hour12;
            }
            return String.format("%02d:%s:00", hour24, minuteCombo.getValue());
        }

        /** Sends UPDATE_RESERVATION; on success advances to Settlement and refreshes
         *  the list, on failure surfaces the server message and stays on Review. */
        private void submit() {
            network.updateReservation(original.getId(), visitDate, visitTime, partySize).thenAccept(res -> {
                if (!res.isSuccess()) {
                    showError(res.getMessage()); // capacity / status rejection — stay on Review
                    return;
                }
                if (res.getData() instanceof ReservationUpdateResultDTO r) {
                    result = r;
                }
                showStep(2);
                reload(); // live refresh of the (hidden) list; shown again on finish
            });
        }

        /** Restores the list view, replacing the wizard. */
        private void finish() {
            screenRoot.getChildren().setAll(savedView);
        }

        private Label fieldLabel(String text) {
            Label l = new Label(text);
            l.getStyleClass().add("field-label");
            return l;
        }

        private Label hintLabel(String text) {
            Label l = new Label(text);
            l.getStyleClass().add("hint-text");
            l.setWrapText(true);
            return l;
        }

        private Button primary(String text) {
            Button b = new Button(text);
            b.getStyleClass().add("btn-primary");
            return b;
        }

        private Button secondary(String text) {
            Button b = new Button(text);
            b.getStyleClass().add("btn-secondary");
            return b;
        }

        private void showError(String msg) {
            errorLbl.setText(msg);
            errorLbl.setVisible(true);
            errorLbl.setManaged(true);
        }

        private void hideError() {
            errorLbl.setVisible(false);
            errorLbl.setManaged(false);
        }
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
