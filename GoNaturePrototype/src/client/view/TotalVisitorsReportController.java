package client.view;

import client.app.Session;
import client.service.NetworkService;
import common.dto.TotalVisitorsReportDTO;
import common.dto.TotalVisitorsReportRow;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Button;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * Total-Visitors-by-Type report screen, visible to a {@code PARK_MANAGER} only
 * (gated in {@link MainShellController}). The manager picks a date range and clicks
 * Generate to run {@code REPORT_TOTAL_VISITORS} for their <em>own</em> park — there is
 * deliberately no park picker, the server derives the park from the session.
 *
 * <p>The centrepiece is a {@link BarChart}: one bar per visitor category
 * ("Individuals" = INDIVIDUAL + FAMILY, "Organized groups" = GROUP) showing the
 * <em>number of visitors</em> in the range, with a per-category summary tile beneath
 * carrying the same figure and a grand-total tile. Every number is read straight off
 * the server's {@link TotalVisitorsReportDTO}; nothing is aggregated client-side.
 *
 * <p><strong>The metric is a headcount, not a visit count</strong> — the bars and
 * tiles count <em>visitors</em> (summed party headcount), so a single group visit of
 * 30 people reads as 30, matching the report's intent. An empty range comes back as a
 * pair of zero bars, not an error.
 *
 * <p>Extends {@link BaseController} for navigation-lifecycle parity; it holds no
 * push subscriptions.
 */
public class TotalVisitorsReportController extends BaseController {

    /** X-axis category label for individual + family visitors (also the bar order). */
    private static final String CAT_INDIVIDUALS = "Individuals";
    /** X-axis category label for organized-group visitors. */
    private static final String CAT_GROUPS      = "Organized groups";

    /** Range start date picker. */
    @FXML private DatePicker fromPicker;
    /** Range end date picker. */
    @FXML private DatePicker toPicker;
    /** Runs the report. */
    @FXML private Button     generateBtn;
    /** Result/toast label for validation and errors. */
    @FXML private Label      resultLabel;
    /** Bar chart of visitor counts per category. */
    @FXML private BarChart<String, Number> chart;
    /** Category (x) axis of the chart. */
    @FXML private CategoryAxis xAxis;
    /** Value (y) axis of the chart. */
    @FXML private NumberAxis   yAxis;
    /** Container for the per-category and grand-total summary tiles. */
    @FXML private HBox       summaryBox;

    // The (NetworkService, Session) shape is what the Navigator's controller
    // factory injects; this screen needs only the network (the server enforces
    // the PARK_MANAGER role and resolves the park), so the session is accepted
    // but unused.
    /**
     * Creates the total-visitors-report controller.
     *
     * @param network the shared network service
     * @param session the current client session
     */
    public TotalVisitorsReportController(NetworkService network, Session session) {
        super(network);
    }

    /** FXML lifecycle hook: sets default dates and fixes the chart categories. */
    @FXML
    private void initialize() {
        // Sensible default window: the last month up to today; the manager adjusts.
        toPicker.setValue(LocalDate.now());
        fromPicker.setValue(LocalDate.now().minusMonths(1));

        // Fix the two categories (and their order) so a zero-count category still
        // shows its (empty) bar rather than disappearing.
        xAxis.setCategories(FXCollections.observableArrayList(CAT_INDIVIDUALS, CAT_GROUPS));
        yAxis.setForceZeroInRange(true);
    }

    /** Generate-button handler: validates inputs and runs REPORT_TOTAL_VISITORS for the own park. */
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

        String fromStr = from.format(DateTimeFormatter.ISO_LOCAL_DATE);
        String toStr   = to.format(DateTimeFormatter.ISO_LOCAL_DATE);

        generateBtn.setDisable(true);
        network.totalVisitorsReport(fromStr, toStr).thenAccept(res -> {
            generateBtn.setDisable(false);
            if (!res.isSuccess()) {
                Widgets.showToast(resultLabel, false, res.getMessage());
                return;
            }
            if (res.getData() instanceof TotalVisitorsReportDTO report) {
                render(report);
            }
        });
    }

    /**
     * Paints the chart and summary tiles from the server's report. Each bar carries
     * the visitor count (headcount) for its category; the tiles repeat the per-category
     * figure and add a grand total. Every number comes straight off the
     * {@link TotalVisitorsReportDTO} rows.
     *
     * @param report the server's report result
     */
    private void render(TotalVisitorsReportDTO report) {
        chart.setTitle(report.getFromDate() + "  →  " + report.getToDate()
                + "    ·    " + report.getParkName());

        XYChart.Series<String, Number> series = new XYChart.Series<>();
        summaryBox.getChildren().clear();

        int total = 0;
        for (TotalVisitorsReportRow row : report.getRows()) {
            String label = "GROUPS".equals(row.getCategory()) ? CAT_GROUPS : CAT_INDIVIDUALS;
            series.getData().add(new XYChart.Data<>(label, row.getVisitorCount()));
            summaryBox.getChildren().add(summaryTile(label, row.getVisitorCount()));
            total += row.getVisitorCount();
        }
        // A trailing grand-total tile so the headline figure is read without summing.
        summaryBox.getChildren().add(summaryTile("All visitors", total));

        chart.getData().clear();
        chart.getData().add(series);
    }

    /**
     * One summary tile: a category (or "All visitors") and its visitor count.
     *
     * @param category     the tile label
     * @param visitorCount the number of visitors for that label
     * @return the summary tile
     */
    private VBox summaryTile(String category, int visitorCount) {
        Label cat = new Label(category);
        cat.getStyleClass().add("stat-tile-label");

        Label value = new Label(visitorCount + (visitorCount == 1 ? " visitor" : " visitors"));
        value.getStyleClass().add("stat-tile-value");

        Label sub = new Label(visitorCount == 0 ? "none in range" : "in selected range");
        sub.getStyleClass().add("stat-tile-sub");

        VBox tile = new VBox(2, cat, value, sub);
        tile.getStyleClass().add("stat-tile");
        HBox.setHgrow(tile, Priority.ALWAYS);
        tile.setMaxWidth(Double.MAX_VALUE);
        return tile;
    }
}
