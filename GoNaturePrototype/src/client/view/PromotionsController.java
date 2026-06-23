package client.view;

import client.app.Session;
import client.service.NetworkService;
import common.dto.ChangeStatus;
import common.dto.ParkDTO;
import common.dto.PromotionDTO;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Promotions screen, visible to a {@code PARK_MANAGER} only (gated in
 * {@link MainShellController}). On show it loads the manager's own park via
 * {@code GET_PARK} (no id — the server derives it from the session) for the
 * heading, then lists their promotions via {@code LIST_PROMOTIONS}.
 *
 * <p>The form sends {@code CREATE_PROMOTION}: the promotion is stored PENDING on
 * the server and affects no price until a department manager approves it. The
 * target park is derived server-side from the login, never sent. After a
 * successful submit the list is reloaded so the new PENDING row appears.
 *
 * <p>Mirrors {@link ParkParamsController} (same form/list shape, same
 * {@link NetworkService} usage); extends {@link BaseController} for navigation
 * lifecycle parity and holds no push subscriptions.
 */
public class PromotionsController extends BaseController {

    /** Name of the manager's park. */
    @FXML private Label      parkNameLabel;
    /** Promotion name input. */
    @FXML private TextField  nameField;
    /** Discount-percent input. */
    @FXML private TextField  percentField;
    /** Start-of-window date input. */
    @FXML private DatePicker startPicker;
    /** End-of-window date input. */
    @FXML private DatePicker endPicker;
    /** Submits the promotion for approval. */
    @FXML private Button     submitBtn;
    /** Result/toast label for submit feedback. */
    @FXML private Label      resultLabel;
    /** Header label showing the promotion count. */
    @FXML private Label      listHeaderLabel;
    /** Container the promotion rows are rendered into. */
    @FXML private VBox       promotionsBox;
    /** Manually reloads the manager's promotions. */
    @FXML private Button     refreshBtn;

    // The (NetworkService, Session) shape is what the Navigator's controller
    // factory injects; this screen needs only the network (the server derives the
    // park and enforces the PARK_MANAGER role), so the session is accepted but unused.
    /**
     * Creates the promotions controller.
     *
     * @param network the shared network service
     * @param session the current client session
     */
    public PromotionsController(NetworkService network, Session session) {
        super(network);
    }

    /** FXML lifecycle hook: loads the manager's park heading and their promotions. */
    @FXML
    private void initialize() {
        loadPark();
        loadPromotions();
    }

    /** Refresh-button handler: reloads the manager's promotions. */
    @FXML
    private void onRefresh() {
        loadPromotions();
    }

    /** Fetches the manager's own park to label the form heading. */
    private void loadPark() {
        network.getMyPark().thenAccept(res -> {
            if (res.isSuccess() && res.getData() instanceof ParkDTO p) {
                parkNameLabel.setText(p.getName());
            } else {
                parkNameLabel.setText("Your park");
            }
        });
    }

    /** Submit handler: validates the inputs and sends CREATE_PROMOTION. */
    @FXML
    private void onSubmit() {
        String name = nameField.getText() == null ? "" : nameField.getText().trim();
        if (name.isEmpty()) {
            Widgets.showToast(resultLabel, false, "Enter a name for the promotion");
            return;
        }

        String rawPercent = percentField.getText() == null ? "" : percentField.getText().trim();
        int percent;
        try {
            percent = Integer.parseInt(rawPercent);
        } catch (NumberFormatException ex) {
            Widgets.showToast(resultLabel, false, "Enter a whole number for the discount percent");
            return;
        }
        if (percent < 0 || percent > 100) {
            Widgets.showToast(resultLabel, false, "The discount percent must be between 0 and 100");
            return;
        }

        LocalDate start = startPicker.getValue();
        LocalDate end = endPicker.getValue();
        if (start == null || end == null) {
            Widgets.showToast(resultLabel, false, "Choose a start date and an end date");
            return;
        }
        if (end.isBefore(start)) {
            Widgets.showToast(resultLabel, false, "The end date cannot be before the start date");
            return;
        }

        submitBtn.setDisable(true);
        String startIso = start.format(DateTimeFormatter.ISO_LOCAL_DATE);
        String endIso = end.format(DateTimeFormatter.ISO_LOCAL_DATE);
        network.createPromotion(name, percent, startIso, endIso).thenAccept(res -> {
            submitBtn.setDisable(false);
            Widgets.showToast(resultLabel, res.isSuccess(), res.getMessage());
            if (res.isSuccess()) {
                nameField.clear();
                percentField.clear();
                startPicker.setValue(null);
                endPicker.setValue(null);
                loadPromotions();
            }
        });
    }

    /** Loads the manager's promotions from the server and repaints the list. */
    private void loadPromotions() {
        refreshBtn.setDisable(true);
        network.listPromotions().thenAccept(res -> {
            refreshBtn.setDisable(false);
            if (!res.isSuccess()) {
                Widgets.showToast(resultLabel, false, res.getMessage());
                return;
            }
            List<PromotionDTO> rows = new ArrayList<>();
            if (res.getData() instanceof List<?> raw) {
                for (Object o : raw) {
                    if (o instanceof PromotionDTO p) rows.add(p);
                }
            }
            populate(rows);
        });
    }

    /**
     * Renders the header and one row per promotion (or an empty-state row).
     *
     * @param rows the manager's promotions to display
     */
    private void populate(List<PromotionDTO> rows) {
        listHeaderLabel.setText("MY PROMOTIONS (" + rows.size() + ")");
        promotionsBox.getChildren().clear();

        if (rows.isEmpty()) {
            Label none = new Label("No promotions defined yet.");
            none.getStyleClass().addAll("history-cell", "muted");
            HBox row = new HBox(none);
            row.getStyleClass().add("history-row");
            promotionsBox.getChildren().add(row);
            return;
        }

        for (int i = 0; i < rows.size(); i++) {
            promotionsBox.getChildren().add(dataRow(rows.get(i), i < rows.size() - 1));
        }
    }

    /**
     * Builds one promotion row: name, discount, date window and a status tag.
     *
     * @param p           the promotion to render
     * @param withDivider whether to draw a divider below the row
     * @return the assembled row
     */
    private HBox dataRow(PromotionDTO p, boolean withDivider) {
        Label nameLbl    = cell(p.getName(),                          180);
        Label percentLbl = cell(p.getDiscountPercent() + "%",          70);
        Label windowLbl  = cell(p.getStartDate() + "  →  " + p.getEndDate(), 190);

        Label statusTag = new Label(p.getStatus().name());
        statusTag.getStyleClass().addAll("status-tag", statusClass(p.getStatus()));

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox row = new HBox(nameLbl, percentLbl, windowLbl, spacer, statusTag);
        row.getStyleClass().add("history-row");
        if (withDivider) row.getStyleClass().add("with-divider");
        return row;
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
     * Maps a promotion status to an existing {@code status-tag} colour class:
     * PENDING amber, APPROVED green (reusing {@code confirmed}), REJECTED red
     * (reusing {@code cancelled}).
     *
     * @param status the promotion status
     * @return the css modifier class for the status tag
     */
    private static String statusClass(ChangeStatus status) {
        switch (status) {
            case APPROVED: return "confirmed";
            case REJECTED: return "cancelled";
            case PENDING:
            default:       return "pending";
        }
    }
}
