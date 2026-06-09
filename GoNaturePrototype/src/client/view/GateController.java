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
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

/**
 * The park-gate screen, visible to a {@code PARK_EMPLOYEE} only (gated in
 * {@link MainShellController}; the server also re-checks the role on every op).
 * One screen with a live occupancy header plus three actions:
 *
 * <ul>
 *   <li><b>Entry</b> — confirmation code + visitor id → {@code ENTER_VISIT}.</li>
 *   <li><b>Exit</b> — confirmation code or visitor id → {@code EXIT_VISIT}.</li>
 *   <li><b>Casual walk-in</b> — party size + visit type + optional visitor id →
 *       {@code CASUAL_VISIT}, showing the <em>server-computed</em> price.</li>
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
    @FXML private Label occupancyValue;
    @FXML private Label occupancySub;
    @FXML private Button refreshBtn;

    // Entry section.
    @FXML private TextField entryCodeField;
    @FXML private TextField entryVisitorField;
    @FXML private Button    entryBtn;
    @FXML private Label     entryResult;

    // Exit section.
    @FXML private TextField exitCodeField;
    @FXML private TextField exitVisitorField;
    @FXML private Button    exitBtn;
    @FXML private Label     exitResult;

    // Casual walk-in section.
    @FXML private Spinner<Integer>    casualPartySpinner;
    @FXML private ComboBox<VisitType> casualTypeCombo;
    @FXML private TextField           casualVisitorField;
    @FXML private Button              casualBtn;
    @FXML private Label               casualResult;
    @FXML private VBox                casualConfirmBox;

    // The (NetworkService, Session) shape is what the Navigator's controller
    // factory injects; this screen needs only the network (the server derives the
    // park from the login), so the session is accepted but unused.
    public GateController(NetworkService network, Session session) {
        super(network);
    }

    @FXML
    private void initialize() {
        casualPartySpinner.setValueFactory(
                new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 100, 1));
        casualTypeCombo.getItems().setAll(VisitType.INDIVIDUAL, VisitType.FAMILY, VisitType.GROUP);
        casualTypeCombo.getSelectionModel().selectFirst();
        hideCasualPrice();
    }

    /** Refresh the occupancy header when the screen becomes active. */
    @Override
    public void onShow() {
        refreshOccupancy();
    }

    /* ---------- Occupancy header ------------------------------------------- */

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

    /* ---------- Casual walk-in --------------------------------------------- */

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
            if (v != null) showCasualPrice(v);
            casualVisitorField.clear();
            refreshOccupancy();
        });
    }

    /**
     * Renders the server-priced confirmation for a casual walk-in. The total comes
     * straight from the returned {@link VisitDTO#getPriceCents()} — never computed
     * on the client.
     *
     * @param v the visit returned by the server
     */
    private void showCasualPrice(VisitDTO v) {
        casualConfirmBox.getChildren().clear();

        Label title = new Label("✓ Admitted");
        title.getStyleClass().add("payment-title");

        int cents = v.getPriceCents() == null ? 0 : v.getPriceCents();
        casualConfirmBox.getChildren().addAll(
                title,
                priceRow("Party", v.getHeadcount() + " visitor(s)"),
                priceRow("Type", v.getVisitType() == null ? "—" : v.getVisitType().name()),
                priceRow("Price", String.format("₪%.2f", cents / 100.0)));

        casualConfirmBox.setVisible(true);
        casualConfirmBox.setManaged(true);
    }

    /** Builds one key/value line for the casual price panel. */
    private HBox priceRow(String key, String value) {
        Label k = new Label(key);
        k.getStyleClass().add("key");
        Label val = new Label(value);
        val.getStyleClass().add("val");
        HBox row = new HBox(k, val);
        row.getStyleClass().add("result-row");
        return row;
    }

    private void hideCasualPrice() {
        casualConfirmBox.getChildren().clear();
        casualConfirmBox.setVisible(false);
        casualConfirmBox.setManaged(false);
    }

    /* ---------- helpers ----------------------------------------------------- */

    /** Trimmed text of a field, never {@code null}. */
    private static String text(TextField f) {
        return f.getText() == null ? "" : f.getText().trim();
    }
}
