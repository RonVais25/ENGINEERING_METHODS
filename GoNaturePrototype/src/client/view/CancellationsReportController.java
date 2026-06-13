package client.view;

import client.app.Session;
import client.service.NetworkService;
import common.dto.CancellationsReportDTO;
import common.dto.CancellationsReportRow;
import common.dto.ParkDTO;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Cancellations report screen, visible to a {@code DEPT_MANAGER} only (gated in
 * {@link MainShellController}). The manager picks a date range and a park — or
 * "All parks" for the whole region — and clicks Generate to run
 * {@code REPORT_CANCELLATIONS}.
 *
 * <p>This is the tabular report: one row per day showing how many reservations
 * were cancelled and how many were marked no-show, followed by a totals row and a
 * summary of the range totals and the average per day. Every figure is read
 * straight off the server's {@link CancellationsReportDTO} — nothing is
 * aggregated client-side. The manual row layout mirrors
 * {@link ApprovalQueueController} for visual consistency.
 *
 * <p>Extends {@link BaseController} for navigation-lifecycle parity; it holds no
 * push subscriptions.
 */
public class CancellationsReportController extends BaseController {

    /** Park dropdown entry: carries the id (null for "All parks") but renders the name. */
    private record ParkOption(Integer id, String name) {
        @Override public String toString() { return name; }
    }

    @FXML private DatePicker           fromPicker;
    @FXML private DatePicker           toPicker;
    @FXML private ComboBox<ParkOption> parkCombo;
    @FXML private Button               generateBtn;
    @FXML private Label                resultLabel;
    @FXML private Label                cardHeaderLabel;
    @FXML private VBox                 tableBox;
    @FXML private HBox                 summaryBox;

    // The (NetworkService, Session) shape is what the Navigator's controller
    // factory injects; this screen needs only the network (the server enforces
    // the DEPT_MANAGER role), so the session is accepted but unused.
    public CancellationsReportController(NetworkService network, Session session) {
        super(network);
    }

    @FXML
    private void initialize() {
        // TODO: fix the dept-manager reports screen layout to fit the content area better.
        toPicker.setValue(LocalDate.now());
        fromPicker.setValue(LocalDate.now().minusMonths(1));
        tableBox.getChildren().setAll(headerRow());
        loadParks();
    }

    /**
     * Populates the park dropdown from the server via {@code LIST_PARKS}, with an
     * "All parks" option (id {@code null}) at the top for a region-wide report.
     */
    private void loadParks() {
        parkCombo.getItems().setAll(new ParkOption(null, "All parks"));
        network.listParks().thenAccept(res -> {
            if (!res.isSuccess()) {
                Widgets.showToast(resultLabel, false, res.getMessage());
                return;
            }
            if (res.getData() instanceof List<?> raw) {
                for (Object o : raw) {
                    ParkDTO p = (ParkDTO) o;
                    parkCombo.getItems().add(new ParkOption(p.getId(), p.getName()));
                }
            }
            parkCombo.getSelectionModel().selectFirst(); // default to "All parks"
        });
    }

    @FXML
    private void onGenerate() {
        LocalDate from = fromPicker.getValue();
        LocalDate to   = toPicker.getValue();
        if (from == null || to == null) {
            Widgets.showToast(resultLabel, false, "Please choose both a From and To date");
            return;
        }
        if (to.isBefore(from)) {
            Widgets.showToast(resultLabel, false, "The To date must be on or after the From date");
            return;
        }

        ParkOption park = parkCombo.getValue();
        Integer parkId  = (park == null) ? null : park.id();

        String fromStr = from.format(DateTimeFormatter.ISO_LOCAL_DATE);
        String toStr   = to.format(DateTimeFormatter.ISO_LOCAL_DATE);

        generateBtn.setDisable(true);
        network.cancellationsReport(fromStr, toStr, parkId).thenAccept(res -> {
            generateBtn.setDisable(false);
            if (!res.isSuccess()) {
                Widgets.showToast(resultLabel, false, res.getMessage());
                return;
            }
            if (res.getData() instanceof CancellationsReportDTO report) {
                render(report);
            }
        });
    }

    /** Paints the per-day table, the totals row and the summary tiles from the DTO. */
    private void render(CancellationsReportDTO report) {
        List<CancellationsReportRow> rows = report.getRows();
        cardHeaderLabel.setText("CANCELLATIONS & NO-SHOWS (" + rows.size()
                + (rows.size() == 1 ? " day)" : " days)"));

        tableBox.getChildren().setAll(headerRow());
        if (rows.isEmpty()) {
            Label none = new Label("No cancellations or no-shows in this range.");
            none.getStyleClass().addAll("history-cell", "muted");
            HBox row = new HBox(none);
            row.getStyleClass().add("history-row");
            tableBox.getChildren().add(row);
        } else {
            for (int i = 0; i < rows.size(); i++) {
                tableBox.getChildren().add(dataRow(rows.get(i), true));
            }
            tableBox.getChildren().add(totalsRow(report));
        }

        renderSummary(report);
    }

    /** The three summary tiles: total cancelled, total no-show, average per day. */
    private void renderSummary(CancellationsReportDTO report) {
        summaryBox.getChildren().setAll(
                summaryTile("Total cancelled", String.valueOf(report.getTotalCancelled())),
                summaryTile("Total no-show",   String.valueOf(report.getTotalNoShow())),
                summaryTile("Average per day",  String.format("%.2f", report.getAvgPerDay())));
    }

    /* ---------- Row + tile builders (mirroring ApprovalQueueController) ----- */

    private HBox headerRow() {
        HBox row = new HBox();
        row.getStyleClass().add("history-header-row");
        row.getChildren().addAll(
                headerCell("DATE",      200),
                headerCell("CANCELLED", 160),
                headerCell("NO-SHOW",   160));
        return row;
    }

    private HBox dataRow(CancellationsReportRow r, boolean withDivider) {
        HBox row = new HBox(
                cell(r.getDate(),                    200),
                cell(String.valueOf(r.getCancelled()), 160),
                cell(String.valueOf(r.getNoShow()),    160));
        row.getStyleClass().add("history-row");
        if (withDivider) row.getStyleClass().add("with-divider");
        return row;
    }

    private HBox totalsRow(CancellationsReportDTO report) {
        HBox row = new HBox(
                totalCell("TOTAL",                            200),
                totalCell(String.valueOf(report.getTotalCancelled()), 160),
                totalCell(String.valueOf(report.getTotalNoShow()),    160));
        row.getStyleClass().addAll("history-row", "total-row");
        return row;
    }

    private VBox summaryTile(String label, String value) {
        Label l = new Label(label);
        l.getStyleClass().add("stat-tile-label");
        Label v = new Label(value);
        v.getStyleClass().add("stat-tile-value");
        VBox tile = new VBox(2, l, v);
        tile.getStyleClass().add("stat-tile");
        HBox.setHgrow(tile, Priority.ALWAYS);
        tile.setMaxWidth(Double.MAX_VALUE);
        return tile;
    }

    private Label headerCell(String text, double w) {
        Label l = new Label(text);
        l.getStyleClass().add("history-header-cell");
        l.setPrefWidth(w);
        return l;
    }

    private Label cell(String text, double w) {
        Label l = new Label(text);
        l.getStyleClass().add("history-cell");
        l.setPrefWidth(w);
        return l;
    }

    private Label totalCell(String text, double w) {
        Label l = new Label(text);
        l.getStyleClass().addAll("history-cell", "total");
        l.setPrefWidth(w);
        return l;
    }
}
