package client.view;

import client.app.Session;
import client.service.NetworkService;
import common.dto.UsageReportDTO;
import common.dto.UsageReportRow;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Button;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Usage report screen, visible to a {@code PARK_MANAGER} only (gated in
 * {@link MainShellController}). The manager picks a date range and clicks Generate
 * to run {@code REPORT_USAGE} for their <em>own</em> park — there is deliberately no
 * park picker, the server derives the park from the session.
 *
 * <p>The centrepiece is a {@link LineChart} with two trend lines over the days of
 * the range:
 * <ul>
 *   <li><b>Peak occupancy</b> — the day's high-water mark of people inside the park
 *       (peak concurrent headcount), a dot at each day.</li>
 *   <li><b>Capacity</b> — a flat, dashed reference line at the park's
 *       {@code maxCapacity}: the "full" mark. Any day whose occupancy dot sits below
 *       it was not full, which is the whole point of the report.</li>
 * </ul>
 * Every figure is read straight off the server's {@link UsageReportDTO} — nothing is
 * aggregated client-side. A day with no visits is plotted as 0, so a quiet range
 * shows a flat baseline well under capacity rather than an empty chart or an error.
 *
 * <p>Extends {@link BaseController} for navigation-lifecycle parity; it holds no
 * push subscriptions.
 */
public class UsageReportController extends BaseController {

    /** Legend name for the daily peak-occupancy trend line (the green, color0 series). */
    private static final String SERIES_OCCUPANCY = "Peak occupancy";
    /** Legend name prefix for the flat capacity reference line (the red, color1 series). */
    private static final String SERIES_CAPACITY  = "Capacity";

    /** Range start date picker. */
    @FXML private DatePicker fromPicker;
    /** Range end date picker. */
    @FXML private DatePicker toPicker;
    /** Runs the report. */
    @FXML private Button     generateBtn;
    /** Result/toast label for validation and errors. */
    @FXML private Label      resultLabel;
    /** Header label showing the day count. */
    @FXML private Label      cardHeaderLabel;
    /** Line chart of daily peak occupancy against the capacity line. */
    @FXML private LineChart<String, Number> chart;
    /** Category (x) axis of the chart. */
    @FXML private CategoryAxis xAxis;
    /** Value (y) axis of the chart. */
    @FXML private NumberAxis   yAxis;
    /** Range-summary line below the chart. */
    @FXML private Label      summaryLabel;

    // The (NetworkService, Session) shape is what the Navigator's controller
    // factory injects; this screen needs only the network (the server enforces
    // the PARK_MANAGER role and resolves the park), so the session is accepted
    // but unused.
    /**
     * Creates the usage-report controller.
     *
     * @param network the shared network service
     * @param session the current client session
     */
    public UsageReportController(NetworkService network, Session session) {
        super(network);
    }

    /** FXML lifecycle hook: sets default dates and the chart's axis/style behaviour. */
    @FXML
    private void initialize() {
        // Sensible default window: the last month up to today; the manager adjusts.
        toPicker.setValue(LocalDate.now());
        fromPicker.setValue(LocalDate.now().minusMonths(1));
        // Always show the 0 baseline so the depth of "not full" is readable.
        yAxis.setForceZeroInRange(true);
        chart.setCreateSymbols(true); // a dot at each day's peak
        // Dashes the capacity (color1) line so it reads as a ceiling; see client.css.
        chart.getStyleClass().add("usage-chart");
    }

    /** Generate-button handler: validates inputs and runs REPORT_USAGE for the own park. */
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
        network.usageReport(fromStr, toStr).thenAccept(res -> {
            generateBtn.setDisable(false);
            if (!res.isSuccess()) {
                Widgets.showToast(resultLabel, false, res.getMessage());
                return;
            }
            if (res.getData() instanceof UsageReportDTO report) {
                render(report);
            }
        });
    }

    /**
     * Paints the per-day occupancy chart and the range-summary line from the
     * server's report. The x-axis categories are the report's days (oldest first);
     * each day carries a peak-occupancy point on one line and the (flat) park
     * capacity on the other, so under-full days are immediately visible. Every
     * figure comes straight off the {@link UsageReportDTO}.
     *
     * @param report the server's report result
     */
    private void render(UsageReportDTO report) {
        List<UsageReportRow> rows = report.getRows();
        int capacity = report.getMaxCapacity();
        cardHeaderLabel.setText("DAILY PEAK OCCUPANCY vs CAPACITY (" + rows.size()
                + (rows.size() == 1 ? " day)" : " days)"));
        chart.setTitle(report.getFromDate() + "  →  " + report.getToDate()
                + "    ·    " + report.getParkName());

        chart.getData().clear();

        // X-axis categories = every day in the range, in order (plain loop, no streams).
        List<String> dates = new ArrayList<>(rows.size());
        for (UsageReportRow r : rows) {
            dates.add(r.getDate());
        }
        xAxis.setCategories(FXCollections.observableArrayList(dates));

        XYChart.Series<String, Number> occupancy = new XYChart.Series<>();
        occupancy.setName(SERIES_OCCUPANCY);
        XYChart.Series<String, Number> capacityLine = new XYChart.Series<>();
        capacityLine.setName(SERIES_CAPACITY + " (" + capacity + ")");

        int peakOfPeaks = 0;
        int daysBelow = 0;
        for (UsageReportRow r : rows) {
            occupancy.getData().add(new XYChart.Data<>(r.getDate(), r.getPeakOccupancy()));
            // A flat capacity point at every date so the "full" line spans the chart.
            capacityLine.getData().add(new XYChart.Data<>(r.getDate(), capacity));
            if (r.getPeakOccupancy() > peakOfPeaks) peakOfPeaks = r.getPeakOccupancy();
            if (r.getPeakOccupancy() < capacity)    daysBelow++;
        }
        // Occupancy first (default-color0 -> green), capacity second (default-color1 -> red).
        chart.getData().add(occupancy);
        chart.getData().add(capacityLine);

        summaryLabel.setText("Capacity: " + capacity
                + "      ·      Highest daily peak: " + peakOfPeaks
                + "      ·      Days below capacity: " + daysBelow + " / " + rows.size());
    }
}
