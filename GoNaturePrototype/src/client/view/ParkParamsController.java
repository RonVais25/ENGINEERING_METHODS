package client.view;

import client.app.Session;
import client.service.NetworkService;
import common.dto.ParamField;
import common.dto.ParkDTO;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;

/**
 * Park-parameter edit screen, visible to a {@code PARK_MANAGER} only (gated in
 * {@link MainShellController}). On show it loads the manager's own park via
 * {@code GET_PARK} (no id — the server derives it from the session) and displays
 * the current {@code max_capacity}, {@code gap_size} and {@code default_stay_minutes}.
 *
 * <p>The form sends {@code REQUEST_PARAM_CHANGE}: the change is stored PENDING on
 * the server and does <strong>not</strong> touch the park until a department
 * manager approves it — so the displayed current values intentionally stay put
 * after a submit. Each successful submission is appended to a session-local
 * "submitted this session" list for at-a-glance feedback (there is no
 * park-manager-facing endpoint to read a request's live status back).
 *
 * <p>Extends {@link BaseController} for navigation-lifecycle parity; it holds no
 * push subscriptions.
 */
public class ParkParamsController extends BaseController {

    /** Name of the manager's park. */
    @FXML private Label              parkNameLabel;
    /** Current max-capacity value. */
    @FXML private Label              maxCapValue;
    /** Current gap-size value. */
    @FXML private Label              gapValue;
    /** Current default-stay value. */
    @FXML private Label              stayValue;
    /** Parameter to change. */
    @FXML private ComboBox<ParamField> fieldCombo;
    /** New value input. */
    @FXML private TextField          newValueField;
    /** Submits the change request. */
    @FXML private Button             submitBtn;
    /** Result/toast label for submit feedback. */
    @FXML private Label              resultLabel;
    /** Container for the session-local "submitted this session" rows. */
    @FXML private VBox               requestsBox;

    /** The manager's park, loaded on show; {@code null} until the load resolves. */
    private ParkDTO park;
    /** Whether the session-local "submitted" list still shows its empty-state row. */
    private boolean requestsEmpty = true;

    // The (NetworkService, Session) shape is what the Navigator's controller
    // factory injects; this screen needs only the network, so the session is
    // accepted but unused (the server derives the park from the login).
    /**
     * Creates the park-parameters controller.
     *
     * @param network the shared network service
     * @param session the current client session
     */
    public ParkParamsController(NetworkService network, Session session) {
        super(network);
    }

    /** FXML lifecycle hook: fills the field dropdown and loads the manager's park. */
    @FXML
    private void initialize() {
        fieldCombo.getItems().setAll(
                ParamField.MAX_CAPACITY, ParamField.GAP_SIZE, ParamField.DEFAULT_STAY_MINUTES);
        fieldCombo.setConverter(new StringConverter<>() {
            @Override public String toString(ParamField f) { return f == null ? "" : labelFor(f); }
            @Override public ParamField fromString(String s) { return null; }
        });
        fieldCombo.getSelectionModel().selectFirst();
        loadPark();
    }

    /** Fetches the manager's own park and fills the current-values panel. */
    private void loadPark() {
        submitBtn.setDisable(true);
        network.getMyPark().thenAccept(res -> {
            if (!res.isSuccess() || !(res.getData() instanceof ParkDTO p)) {
                String msg = res.getMessage() == null ? "Could not load your park." : res.getMessage();
                Widgets.showToast(resultLabel, false, msg);
                return;
            }
            this.park = p;
            parkNameLabel.setText(p.getName());
            maxCapValue.setText(String.valueOf(p.getMaxCapacity()));
            gapValue.setText(String.valueOf(p.getGapSize()));
            stayValue.setText(String.valueOf(p.getDefaultStayMinutes()));
            submitBtn.setDisable(false);
        });
    }

    /** Submit handler: validates the new value and sends REQUEST_PARAM_CHANGE. */
    @FXML
    private void onSubmit() {
        if (park == null) {
            Widgets.showToast(resultLabel, false, "Your park hasn't loaded yet — try again in a moment");
            return;
        }
        ParamField field = fieldCombo.getValue();
        if (field == null) {
            Widgets.showToast(resultLabel, false, "Choose a parameter to change");
            return;
        }

        String raw = newValueField.getText() == null ? "" : newValueField.getText().trim();
        int newValue;
        try {
            newValue = Integer.parseInt(raw);
        } catch (NumberFormatException ex) {
            Widgets.showToast(resultLabel, false, "Enter a whole number for the new value");
            return;
        }
        if (newValue < 0) {
            Widgets.showToast(resultLabel, false, "The new value must be zero or greater");
            return;
        }

        int oldValue = currentValue(field);
        if (newValue == oldValue) {
            Widgets.showToast(resultLabel, false, "That is already the current value for " + labelFor(field));
            return;
        }

        submitBtn.setDisable(true);
        network.requestParamChange(field, newValue).thenAccept(res -> {
            submitBtn.setDisable(false);
            Widgets.showToast(resultLabel, res.isSuccess(), res.getMessage());
            if (res.isSuccess()) {
                addSubmittedRow(field, oldValue, newValue);
                newValueField.clear();
            }
        });
    }

    /**
     * Reads the loaded park's current value for the chosen field.
     *
     * @param field the parameter field
     * @return the park's current value for that field
     */
    private int currentValue(ParamField field) {
        switch (field) {
            case MAX_CAPACITY:         return park.getMaxCapacity();
            case GAP_SIZE:             return park.getGapSize();
            case DEFAULT_STAY_MINUTES: return park.getDefaultStayMinutes();
            default:                   return 0;
        }
    }

    /**
     * Prepends a row to the session-local "submitted this session" list.
     *
     * @param field    the changed parameter
     * @param oldValue the previous value
     * @param newValue the requested new value
     */
    private void addSubmittedRow(ParamField field, int oldValue, int newValue) {
        if (requestsEmpty) {
            requestsBox.getChildren().clear();
            requestsEmpty = false;
        }

        Label fieldLbl  = cell(labelFor(field), 200);
        Label changeLbl = cell(oldValue + "  →  " + newValue, 140);
        Label statusTag = new Label("PENDING");
        statusTag.getStyleClass().addAll("status-tag", "pending");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox row = new HBox(fieldLbl, changeLbl, spacer, statusTag);
        row.getStyleClass().addAll("history-row", "with-divider");
        requestsBox.getChildren().add(0, row);
    }

    /**
     * Builds a fixed-width row cell.
     *
     * @param text the cell text
     * @param w    the preferred width
     * @return the cell label
     */
    private Label cell(String text, double w) {
        Label l = new Label(text);
        l.getStyleClass().add("history-cell");
        l.setPrefWidth(w);
        return l;
    }

    /**
     * Friendly label for a parameter field, e.g. {@code MAX_CAPACITY → "Max Capacity"}.
     *
     * @param field the parameter field
     * @return a human-friendly label for the field
     */
    private static String labelFor(ParamField field) {
        switch (field) {
            case MAX_CAPACITY:         return "Max Capacity";
            case GAP_SIZE:             return "Gap Size";
            case DEFAULT_STAY_MINUTES: return "Default Stay (minutes)";
            default:                   return field.name();
        }
    }
}
