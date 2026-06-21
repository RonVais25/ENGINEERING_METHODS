package client.view;

import client.app.Session;
import client.service.NetworkService;
import common.dto.ParameterChangeRequestDTO;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.util.ArrayList;
import java.util.List;

/**
 * Approval-queue screen, visible to a {@code DEPT_MANAGER} only (gated in
 * {@link MainShellController}). On show it loads every PENDING parameter-change
 * request via {@code LIST_PENDING_CHANGES} and lists them in a table: park name,
 * field, old&nbsp;→&nbsp;new, and who requested it.
 *
 * <p>Each row offers Approve / Reject, which send {@code APPROVE_PARAM_CHANGE} /
 * {@code REJECT_PARAM_CHANGE}; on success the list is reloaded so the decided
 * request drops out of the queue. Refresh is manual (on open, after a decision,
 * or via the Refresh button) — realtime push for this screen is a later session.
 *
 * <p>Extends {@link BaseController} for navigation-lifecycle parity; it holds no
 * push subscriptions.
 */
public class ApprovalQueueController extends BaseController {

    /** Manually reloads the pending queue. */
    @FXML private Button refreshBtn;
    /** Result/toast label for action feedback. */
    @FXML private Label  resultLabel;
    /** Header label showing the pending count. */
    @FXML private Label  cardHeaderLabel;
    /** Container the request rows are rendered into. */
    @FXML private VBox   tableBox;

    // The (NetworkService, Session) shape is what the Navigator's controller
    // factory injects; this screen needs only the network (the server enforces
    // the DEPT_MANAGER role), so the session is accepted but unused.
    /**
     * Creates the approval-queue controller.
     *
     * @param network the shared network service
     * @param session the current client session
     */
    public ApprovalQueueController(NetworkService network, Session session) {
        super(network);
    }

    /** FXML lifecycle hook: loads the pending queue. */
    @FXML
    private void initialize() {
        load();
    }

    /** Refresh-button handler: reloads the pending queue. */
    @FXML
    private void onRefresh() {
        load();
    }

    /** Loads the pending queue from the server and repaints the table. */
    private void load() {
        refreshBtn.setDisable(true);
        network.listPendingChanges().thenAccept(res -> {
            refreshBtn.setDisable(false);
            if (!res.isSuccess()) {
                Widgets.showToast(resultLabel, false, res.getMessage());
                return;
            }
            List<ParameterChangeRequestDTO> rows = new ArrayList<>();
            if (res.getData() instanceof List<?> raw) {
                for (Object o : raw) rows.add((ParameterChangeRequestDTO) o);
            }
            populate(rows);
        });
    }

    /**
     * Renders the header and one row per pending request (or an empty-state row).
     *
     * @param rows the pending requests to display
     */
    private void populate(List<ParameterChangeRequestDTO> rows) {
        cardHeaderLabel.setText("PENDING PARAMETER CHANGES (" + rows.size() + ")");
        tableBox.getChildren().setAll(headerRow());

        if (rows.isEmpty()) {
            Label none = new Label("No pending requests. You're all caught up.");
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

    /** {@return the table header row of column titles} */
    private HBox headerRow() {
        HBox row = new HBox();
        row.getStyleClass().add("history-header-row");
        row.getChildren().addAll(
                headerCell("PARK",         140),
                headerCell("FIELD",        180),
                headerCell("CHANGE",       120),
                headerCell("REQUESTED BY", 120),
                headerCell("ACTIONS",        0));
        return row;
    }

    /**
     * Builds one request row: details plus Approve/Reject buttons.
     *
     * @param r           the pending request to render
     * @param withDivider whether to draw a divider below the row
     * @return the assembled row
     */
    private HBox dataRow(ParameterChangeRequestDTO r, boolean withDivider) {
        Label parkLbl   = cell(r.getParkName(),                          140);
        Label fieldLbl  = cell(labelFor(r.getField()),                   180);
        Label changeLbl = cell(r.getOldValue() + " → " + r.getNewValue(), 120);
        // Show the requester's full name; fall back to the id only if it's missing
        // (full_name is a nullable column), mirroring the "Park #id" park fallback.
        String requester = (r.getRequesterName() == null || r.getRequesterName().isBlank())
                ? "User #" + r.getRequestedBy()
                : r.getRequesterName();
        Label byLbl     = cell(requester,                                120);

        Button approveBtn = new Button("Approve");
        approveBtn.getStyleClass().add("btn-secondary");
        approveBtn.setOnAction(e -> decide(r.getId(), true));

        Button rejectBtn = new Button("Reject");
        rejectBtn.getStyleClass().add("btn-secondary");
        rejectBtn.setOnAction(e -> decide(r.getId(), false));

        // Pin each button to its preferred (label) width so a tight row never
        // shrinks "Approve" / "Reject" into ellipsized stubs.
        for (Button b : new Button[] { approveBtn, rejectBtn }) {
            b.setMinWidth(Region.USE_PREF_SIZE);
        }

        HBox actions = new HBox(8, approveBtn, rejectBtn);
        actions.setAlignment(Pos.CENTER_LEFT);

        HBox row = new HBox(parkLbl, fieldLbl, changeLbl, byLbl, actions);
        row.getStyleClass().add("history-row");
        if (withDivider) row.getStyleClass().add("with-divider");
        return row;
    }

    /**
     * Approves or rejects a request, then reloads the queue on success.
     *
     * @param requestId the request to decide
     * @param approve   {@code true} to approve, {@code false} to reject
     */
    private void decide(int requestId, boolean approve) {
        var future = approve
                ? network.approveParamChange(requestId)
                : network.rejectParamChange(requestId);
        future.thenAccept(res -> {
            Widgets.showToast(resultLabel, res.isSuccess(), res.getMessage());
            if (res.isSuccess()) load();
        });
    }

    /**
     * Builds a fixed-width column header cell.
     *
     * @param text the header text
     * @param w    the preferred width, or {@code 0} for natural width
     * @return the header cell label
     */
    private Label headerCell(String text, double w) {
        Label l = new Label(text);
        l.getStyleClass().add("history-header-cell");
        if (w > 0) l.setPrefWidth(w);
        return l;
    }

    /**
     * Builds a fixed-width data cell.
     *
     * @param text the cell text
     * @param w    the preferred width
     * @return the data cell label
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
    private static String labelFor(common.dto.ParamField field) {
        switch (field) {
            case MAX_CAPACITY:         return "Max Capacity";
            case GAP_SIZE:             return "Gap Size";
            case DEFAULT_STAY_MINUTES: return "Default Stay (minutes)";
            default:                   return field.name();
        }
    }
}
