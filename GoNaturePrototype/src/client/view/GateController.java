package client.view;

import client.app.Session;
import client.service.NetworkService;
import common.dto.OccupancyDTO;
import common.dto.VisitDTO;
import common.dto.VisitType;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

/**
 * The park-gate screen, visible to a {@code PARK_EMPLOYEE} only (gated in
 * {@link MainShellController}; the server also re-checks the role on every op).
 * An always-visible live-occupancy header sits above an <b>Entry / Exit</b>
 * segmented toggle — only one mode's cards are shown at a time, so the screen
 * fits without scrolling. Each mode pairs a reservation action with its casual
 * counterpart:
 *
 * <ul>
 *   <li><b>Entry · reservation</b> — confirmation code + visitor id → {@code ENTER_VISIT}.</li>
 *   <li><b>Entry · casual walk-in</b> — party size + visit type + optional visitor id →
 *       {@code CASUAL_VISIT}, showing the <em>server-computed</em> price and a
 *       <b>ticket number</b> (the visit id) for the employee to note.</li>
 *   <li><b>Exit · reservation</b> — confirmation code or visitor id → {@code EXIT_VISIT}.</li>
 *   <li><b>Exit · casual walk-in</b> — the ticket number issued at admission →
 *       {@code EXIT_VISIT}. This is the only way to exit an anonymous walk-in, which
 *       has no confirmation code or visitor id to look it up by.</li>
 * </ul>
 *
 * <p>The occupancy header refreshes on show, after every successful action, and
 * on demand via the Refresh button. The employee's park is always derived
 * server-side from the session, so no park id is sent from here. The casual
 * price shown comes straight from the returned {@link VisitDTO} — it is never
 * recomputed client-side.
 *
 * <p>Extends {@link BaseController} for navigation-lifecycle parity; it holds no
 * push subscriptions (cross-client realtime occupancy push is a later session —
 * refresh here is manual / after-action).
 */
public class GateController extends BaseController {

    // Occupancy header.
    /** Live occupancy value ("current / limit"). */
    @FXML private Label occupancyValue;
    /** Occupancy sub-line ("(N free)"). */
    @FXML private Label occupancySub;
    /** Manually refreshes the occupancy header. */
    @FXML private Button refreshBtn;

    // Entry / Exit mode toggle and the two mode containers.
    /** Tab selecting Entry mode. */
    @FXML private ToggleButton entryTab;
    /** Tab selecting Exit mode. */
    @FXML private ToggleButton exitTab;
    /** Container of the Entry-mode cards. */
    @FXML private VBox         entryMode;
    /** Container of the Exit-mode cards. */
    @FXML private VBox         exitMode;

    // Entry · reservation section.
    /** Entry: confirmation-code input. */
    @FXML private TextField entryCodeField;
    /** Entry: visitor-id input. */
    @FXML private TextField entryVisitorField;
    /** Entry: admit-by-reservation button. */
    @FXML private Button    entryBtn;
    /** Entry: reservation result/toast label. */
    @FXML private Label     entryResult;

    // Entry · casual walk-in section.
    /** Casual entry: party-size spinner. */
    @FXML private Spinner<Integer>    casualPartySpinner;
    /** Casual entry: visit-type dropdown. */
    @FXML private ComboBox<VisitType> casualTypeCombo;
    /** Casual entry: optional visitor-id input. */
    @FXML private TextField           casualVisitorField;
    /** Casual entry: admit walk-in button. */
    @FXML private Button              casualBtn;
    /** Casual entry: result/toast label. */
    @FXML private Label               casualResult;
    /** Casual entry: server-priced ticket confirmation panel. */
    @FXML private VBox                casualConfirmBox;

    // Exit · reservation section.
    /** Exit: confirmation-code input. */
    @FXML private TextField exitCodeField;
    /** Exit: visitor-id input. */
    @FXML private TextField exitVisitorField;
    /** Exit: record-exit-by-reservation button. */
    @FXML private Button    exitBtn;
    /** Exit: reservation result/toast label. */
    @FXML private Label     exitResult;

    // Exit · casual walk-in section (by ticket number).
    /** Casual exit: ticket-number input. */
    @FXML private TextField casualExitTicketField;
    /** Casual exit: record-exit button. */
    @FXML private Button    casualExitBtn;
    /** Casual exit: result/toast label. */
    @FXML private Label     casualExitResult;

    // The (NetworkService, Session) shape is what the Navigator's controller
    // factory injects; this screen needs only the network (the server derives the
    // park from the login), so the session is accepted but unused.
    /**
     * Creates the gate controller.
     *
     * @param network the shared network service
     * @param session the current client session
     */
    public GateController(NetworkService network, Session session) {
        super(network);
    }

    /** FXML lifecycle hook: configures the inputs and the Entry/Exit toggle. */
    @FXML
    private void initialize() {
        casualPartySpinner.setValueFactory(
                new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 100, 1));
        casualTypeCombo.getItems().setAll(VisitType.INDIVIDUAL, VisitType.FAMILY, VisitType.GROUP);
        casualTypeCombo.getSelectionModel().selectFirst();
        hideCasualPrice();

        // Entry / Exit are mutually exclusive: exactly one mode of cards shows at
        // a time (keeps the screen from scrolling). Clicking the active tab must
        // not deselect it — that would hide both modes.
        ToggleGroup modeGroup = new ToggleGroup();
        entryTab.setToggleGroup(modeGroup);
        exitTab.setToggleGroup(modeGroup);
        entryTab.setSelected(true);
        modeGroup.selectedToggleProperty().addListener((obs, old, sel) -> {
            if (sel == null) {
                old.setSelected(true);
            } else {
                showEntryMode(sel == entryTab);
            }
        });
        showEntryMode(true);
    }

    /**
     * Shows the Entry-mode cards and hides the Exit-mode cards (or vice versa).
     *
     * @param entry {@code true} to show Entry mode, {@code false} for Exit mode
     */
    private void showEntryMode(boolean entry) {
        entryMode.setVisible(entry);
        entryMode.setManaged(entry);
        exitMode.setVisible(!entry);
        exitMode.setManaged(!entry);
    }

    /** Refresh the occupancy header when the screen becomes active. */
    @Override
    public void onShow() {
        refreshOccupancy();
    }

    /* ---------- Occupancy header ------------------------------------------- */

    /** Refresh-button handler: repaints the occupancy header. */
    @FXML
    private void onRefresh() {
        refreshOccupancy();
    }

    /** Fetches live occupancy for the employee's park and repaints the header. */
    private void refreshOccupancy() {
        refreshBtn.setDisable(true);
        network.currentOccupancy().thenAccept(res -> {
            refreshBtn.setDisable(false);
            if (!res.isSuccess() || !(res.getData() instanceof OccupancyDTO o)) {
                occupancyValue.setText("—");
                occupancySub.setText(res.getMessage() == null ? "Could not load occupancy" : res.getMessage());
                return;
            }
            // Denominator is the gate-adjusted ceiling (maxCapacity - gapSize),
            // matching the capacity rule the server enforces for walk-ins.
            int limit = o.getMaxCapacity() - o.getGapSize();
            occupancyValue.setText(o.getCurrent() + " / " + limit);
            occupancySub.setText("(" + o.getAvailable() + " free)");
        });
    }

    /* ---------- Entry ------------------------------------------------------- */

    /** Entry handler: validates the code + visitor id and admits via ENTER_VISIT. */
    @FXML
    private void onEnter() {
        String codeRaw = text(entryCodeField);
        if (codeRaw.isEmpty()) {
            Widgets.showToast(entryResult, false, "Enter the booking's confirmation code");
            return;
        }
        int code;
        try {
            code = Integer.parseInt(codeRaw);
        } catch (NumberFormatException ex) {
            Widgets.showToast(entryResult, false, "Confirmation code must be a number");
            return;
        }
        String visRaw = text(entryVisitorField);
        long visitorId;
        try {
            visitorId = Long.parseLong(visRaw);
        } catch (NumberFormatException ex) {
            Widgets.showToast(entryResult, false, "Enter the visitor's numeric ID");
            return;
        }

        entryBtn.setDisable(true);
        network.enterVisit(code, visitorId).thenAccept(res -> {
            entryBtn.setDisable(false);
            if (!res.isSuccess()) {
                Widgets.showToast(entryResult, false, res.getMessage());
                return;
            }
            int n = (res.getData() instanceof VisitDTO v) ? v.getHeadcount() : 0;
            Widgets.showToast(entryResult, true, "Admitted — " + n + " visitor(s)");
            entryCodeField.clear();
            entryVisitorField.clear();
            refreshOccupancy();
        });
    }

    /* ---------- Exit -------------------------------------------------------- */

    /** Exit handler: records an exit by confirmation code or visitor id. */
    @FXML
    private void onExit() {
        String codeRaw = text(exitCodeField);
        String visRaw  = text(exitVisitorField);
        if (codeRaw.isEmpty() && visRaw.isEmpty()) {
            Widgets.showToast(exitResult, false, "Enter a confirmation code or a visitor ID");
            return;
        }

        Integer code = null;
        Long visitorId = null;
        if (!codeRaw.isEmpty()) {
            try {
                code = Integer.parseInt(codeRaw);
            } catch (NumberFormatException ex) {
                Widgets.showToast(exitResult, false, "Confirmation code must be a number");
                return;
            }
        } else {
            try {
                visitorId = Long.parseLong(visRaw);
            } catch (NumberFormatException ex) {
                Widgets.showToast(exitResult, false, "Visitor ID must be a number");
                return;
            }
        }

        exitBtn.setDisable(true);
        network.exitVisit(code, visitorId).thenAccept(res -> {
            exitBtn.setDisable(false);
            Widgets.showToast(exitResult, res.isSuccess(), res.getMessage());
            if (res.isSuccess()) {
                exitCodeField.clear();
                exitVisitorField.clear();
                refreshOccupancy();
            }
        });
    }

    /* ---------- Casual exit (by ticket number) ----------------------------- */

    /**
     * Closes a casual walk-in by the ticket number issued at admission. This is the
     * only way to exit an anonymous walk-in — it has no confirmation code or visitor
     * id — so the visit id (shown as the ticket number on admit) is the handle.
     */
    @FXML
    private void onCasualExit() {
        String raw = text(casualExitTicketField);
        if (raw.isEmpty()) {
            Widgets.showToast(casualExitResult, false, "Enter the ticket number from admission");
            return;
        }
        int visitId;
        try {
            visitId = Integer.parseInt(raw);
        } catch (NumberFormatException ex) {
            Widgets.showToast(casualExitResult, false, "Ticket number must be a number");
            return;
        }

        casualExitBtn.setDisable(true);
        network.exitVisitByTicket(visitId).thenAccept(res -> {
            casualExitBtn.setDisable(false);
            Widgets.showToast(casualExitResult, res.isSuccess(), res.getMessage());
            if (res.isSuccess()) {
                casualExitTicketField.clear();
                refreshOccupancy();
            }
        });
    }

    /* ---------- Casual walk-in --------------------------------------------- */

    /** Casual-entry handler: admits a walk-in via CASUAL_VISIT and shows the ticket. */
    @FXML
    private void onCasual() {
        hideCasualPrice();

        // Commit any text typed into the editable spinner before reading it.
        try {
            int typed = Integer.parseInt(casualPartySpinner.getEditor().getText().trim());
            if (typed >= 1 && typed <= 100) casualPartySpinner.getValueFactory().setValue(typed);
        } catch (NumberFormatException ignored) {}
        int partySize = casualPartySpinner.getValue();

        VisitType visitType = casualTypeCombo.getValue();
        if (visitType == null) {
            Widgets.showToast(casualResult, false, "Choose a visit type");
            return;
        }

        // Optional visitor id: blank → anonymous walk-in.
        Long visitorId = null;
        String visRaw = text(casualVisitorField);
        if (!visRaw.isEmpty()) {
            try {
                visitorId = Long.parseLong(visRaw);
            } catch (NumberFormatException ex) {
                Widgets.showToast(casualResult, false, "Visitor ID must be a number (or leave it blank)");
                return;
            }
        }

        casualBtn.setDisable(true);
        network.casualVisit(partySize, visitType, visitorId).thenAccept(res -> {
            casualBtn.setDisable(false);
            if (!res.isSuccess()) {
                // e.g. the server's "park full" rejection.
                Widgets.showToast(casualResult, false, res.getMessage());
                return;
            }
            VisitDTO v = (res.getData() instanceof VisitDTO dto) ? dto : null;
            Widgets.showToast(casualResult, true, "Walk-in admitted");
            if (v != null) showCasualTicket(v);
            casualVisitorField.clear();
            refreshOccupancy();
        });
    }

    /**
     * Renders the server-priced confirmation for a casual walk-in, leading with the
     * <b>ticket number</b> (the visit id) for the employee to note — it is the only
     * handle for exiting an anonymous walk-in later, under Exit · Casual. The total
     * comes straight from the returned {@link VisitDTO#getPriceCents()} — never
     * computed on the client.
     *
     * @param v the visit returned by the server
     */
    private void showCasualTicket(VisitDTO v) {
        casualConfirmBox.getChildren().clear();

        Label title = new Label("✓ Admitted — note the ticket number");
        title.getStyleClass().add("payment-title");

        // Ticket number front-and-centre: the employee reads this out / writes it
        // down so the visitor can be exited via Exit · Casual.
        Label ticket = new Label("Ticket #" + v.getId());
        ticket.getStyleClass().add("stat-card-value");
        Label ticketHint = new Label("Enter this under Exit · Casual to record their exit.");
        ticketHint.getStyleClass().add("hint-text");
        ticketHint.setWrapText(true);

        int cents = v.getPriceCents() == null ? 0 : v.getPriceCents();
        casualConfirmBox.getChildren().addAll(
                title,
                ticket,
                ticketHint,
                priceRow("Party", v.getHeadcount() + " visitor(s)"),
                priceRow("Type", v.getVisitType() == null ? "—" : v.getVisitType().name()),
                priceRow("Price", String.format("₪%.2f", cents / 100.0)));

        casualConfirmBox.setVisible(true);
        casualConfirmBox.setManaged(true);
    }

    /**
     * Builds one key/value line for the casual price panel.
     *
     * @param key   the line label
     * @param value the line value
     * @return the key/value row
     */
    private HBox priceRow(String key, String value) {
        Label k = new Label(key);
        k.getStyleClass().add("key");
        Label val = new Label(value);
        val.getStyleClass().add("val");
        HBox row = new HBox(k, val);
        row.getStyleClass().add("result-row");
        return row;
    }

    /** Hides and clears the casual price/ticket panel. */
    private void hideCasualPrice() {
        casualConfirmBox.getChildren().clear();
        casualConfirmBox.setVisible(false);
        casualConfirmBox.setManaged(false);
    }

    /* ---------- helpers ----------------------------------------------------- */

    /**
     * Trimmed text of a field, never {@code null}.
     *
     * @param f the text field to read
     * @return the trimmed text, or {@code ""} if empty
     */
    private static String text(TextField f) {
        return f.getText() == null ? "" : f.getText().trim();
    }
}
