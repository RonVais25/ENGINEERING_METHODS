package client.view;

import client.app.Session;
import client.service.NetworkService;
import common.dto.ParkDTO;
import common.dto.VisitsReportDTO;
import common.dto.VisitsReportRow;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
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
 * Visits-by-Type report screen, visible to a {@code DEPT_MANAGER} only (gated in
 * {@link MainShellController}). The manager picks a date range and a park — or
 * "All parks" for the whole region — and clicks Generate to run
 * {@code REPORT_VISITS_BY_TYPE}.
 *
 * <p>The centerpiece is a {@link BarChart}: one bar per visitor category
 * ("Individuals" = INDIVIDUAL + FAMILY, "Organized groups" = GROUP) showing the
 * visit count, with a per-category summary tile beneath it carrying the average
 * stay in minutes. Every figure is read straight off the server's
 * {@link VisitsReportDTO} — nothing is aggregated client-side.
 *
 * <p>Extends {@link BaseController} for navigation-lifecycle parity; it holds no
 * push subscriptions.
 */
public class VisitsReportController extends BaseController {

    /** X-axis category labels (also the fixed bar order). */
    private static final String CAT_INDIVIDUALS = "Individuals";
    private static final String CAT_GROUPS      = "Organized groups";

    /** Park dropdown entry: carries the id (null for "All parks") but renders the name. */
    private record ParkOption(Integer id, String name) {
        @Override public String toString() { return name; }
    }

    @FXML private DatePicker            fromPicker;
    @FXML private DatePicker            toPicker;
    @FXML private ComboBox<ParkOption>  parkCombo;
    @FXML private Button                generateBtn;
    @FXML private Label                 resultLabel;
    @FXML private BarChart<String, Number> chart;
    @FXML private CategoryAxis          xAxis;
    @FXML private NumberAxis            yAxis;
    @FXML private HBox                  summaryBox;

    // The (NetworkService, Session) shape is what the Navigator's controller
    // factory injects; this screen needs only the network (the server enforces
    // the DEPT_MANAGER role), so the session is accepted but unused.
    public VisitsReportController(NetworkService network, Session session) {
        super(network);
    }

    @FXML
    private void initialize() {
        // TODO: fix the dept-manager reports screen layout to fit the content area better.
        // Sensible default window: the last month up to today; the manager adjusts.
        toPicker.setValue(LocalDate.now());
        fromPicker.setValue(LocalDate.now().minusMonths(1));

        // Fix the two categories (and their order) so a zero-count category still
        // shows its (empty) bar rather than disappearing.
        xAxis.setCategories(FXCollections.observableArrayList(CAT_INDIVIDUALS, CAT_GROUPS));
        yAxis.setForceZeroInRange(true);

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
        String parkLabel = (park == null) ? "All parks" : park.name();

        String fromStr = from.format(DateTimeFormatter.ISO_LOCAL_DATE);
        String toStr   = to.format(DateTimeFormatter.ISO_LOCAL_DATE);

        generateBtn.setDisable(true);
        network.visitsReport(fromStr, toStr, parkId).thenAccept(res -> {
            generateBtn.setDisable(false);
            if (!res.isSuccess()) {
                Widgets.showToast(resultLabel, false, res.getMessage());
                return;
            }
            if (res.getData() instanceof VisitsReportDTO report) {
                render(report, parkLabel);
            }
        });
    }

    /**
     * Paints the chart and summary tiles from the server's report. Bars carry the
     * visit count per category; the tiles carry the average stay (minutes). Both
     * come straight from the {@link VisitsReportDTO} rows.
     */
    private void render(VisitsReportDTO report, String parkLabel) {
        chart.setTitle(report.getFromDate() + "  →  " + report.getToDate() + "    ·    " + parkLabel);

        XYChart.Series<String, Number> series = new XYChart.Series<>();
        summaryBox.getChildren().clear();

        for (VisitsReportRow row : report.getRows()) {
            String label = "GROUPS".equals(row.getCategory()) ? CAT_GROUPS : CAT_INDIVIDUALS;
            series.getData().add(new XYChart.Data<>(label, row.getVisitCount()));
            summaryBox.getChildren().add(summaryTile(label, row.getVisitCount(), row.getAvgStayMinutes()));
        }
        chart.getData().clear();
        chart.getData().add(series);
    }

    /** One per-category summary tile: category, visit count, and average stay. */
    private VBox summaryTile(String category, int visitCount, double avgStayMinutes) {
        Label cat = new Label(category);
        cat.getStyleClass().add("stat-tile-label");

        Label value = new Label(visitCount + (visitCount == 1 ? " visit" : " visits"));
        value.getStyleClass().add("stat-tile-value");

        Label sub = new Label(visitCount == 0
                ? "no visits in range"
                : String.format("avg stay %.0f min", avgStayMinutes));
        sub.getStyleClass().add("stat-tile-sub");

        VBox tile = new VBox(2, cat, value, sub);
        tile.getStyleClass().add("stat-tile");
        HBox.setHgrow(tile, Priority.ALWAYS);
        tile.setMaxWidth(Double.MAX_VALUE);
        return tile;
    }
}
