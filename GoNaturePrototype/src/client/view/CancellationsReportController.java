package client.view;

import client.app.Session;
import client.service.NetworkService;
import common.dto.CancellationsReportDTO;
import common.dto.CancellationsReportRow;
import common.dto.ParkDTO;
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
import javafx.scene.layout.VBox;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Cancellations report screen, visible to a {@code DEPT_MANAGER} only (gated in
 * {@link MainShellController}). The manager picks a date range and a park — or
 * "All parks" for the whole region — and clicks Generate to run
 * {@code REPORT_CANCELLATIONS}.
 *
 * <p>The centrepiece is a {@link BarChart}: one category per active day along the
 * x-axis, with two grouped bars — "Cancelled" and "No-show" — carrying the day's
 * counts on the y-axis. A small summary line beneath it repeats the range totals
 * and the average per day. Every figure is read straight off the server's
 * {@link CancellationsReportDTO} — nothing is aggregated client-side. The chart
 * mirrors {@link VisitsReportController} for visual consistency.
 *
 * <p>Extends {@link BaseController} for navigation-lifecycle parity; it holds no
 * push subscriptions.
 */
public class CancellationsReportController extends BaseController {

    /** Series (legend) names, also the fixed bar order within each date group. */
    private static final String SERIES_CANCELLED = "Cancelled";
/** Stores the series no show value used by this component. */
    private static final String SERIES_NO_SHOW   = "No-show";

    /** Park dropdown entry: carries the id (null for "All parks") but renders the name. */
    private record ParkOption(Integer id, String name) {
        @Override public String toString() { return name; }
    }
/** Stores the from picker value used by this component. */

    @FXML private DatePicker               fromPicker;
    @FXML private DatePicker               toPicker;
    @FXML private ComboBox<ParkOption>     parkCombo;
    @FXML private Button                   generateBtn;
    @FXML private Label                    resultLabel;
    @FXML private Label                    cardHeaderLabel;
    @FXML private BarChart<String, Number> chart;
    @FXML private CategoryAxis             xAxis;
    @FXML private NumberAxis               yAxis;
    @FXML private VBox                     placeholderBox;
    @FXML private Label                    summaryLabel;

    // The (NetworkService, Session) shape is what the Navigator's controller
    // factory injects; this screen needs only the network (the server enforces
    // the DEPT_MANAGER role), so the session is accepted but unused.
/**
 * Creates a new cancellations report controller instance.
 * @param network value supplied to the operation
 * @param session value supplied to the operation
 */
    public CancellationsReportController(NetworkService network, Session session) {
        super(network);
    }
/**
 * Initializes the controller after its FXML fields are injected.
 */

    @FXML
    private void initialize() {
        // Sensible default window: the last month up to today; the manager adjusts.
        toPicker.setValue(LocalDate.now());
        fromPicker.setValue(LocalDate.now().minusMonths(1));
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
/**
 * Performs the on generate operation.
 */

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

    /**
     * Paints the per-day chart and the range-summary line from the server's report.
     * The x-axis categories are the report's dates (oldest first); each date carries
     * a "Cancelled" and a "No-show" bar. An empty range shows a placeholder instead
     * of a bare chart. Every figure comes straight off the {@link CancellationsReportDTO}.
     */
    private void render(CancellationsReportDTO report) {
        List<CancellationsReportRow> rows = report.getRows();
        cardHeaderLabel.setText("CANCELLATIONS & NO-SHOWS (" + rows.size()
                + (rows.size() == 1 ? " day)" : " days)"));

        if (rows.isEmpty()) {
            chart.getData().clear();
            showPlaceholder(true);
            return;
        }

        // X-axis categories = the report's dates, in order (plain loop, no streams).
        List<String> dates = new ArrayList<>(rows.size());
        for (CancellationsReportRow r : rows) {
            dates.add(r.getDate());
        }
        chart.getData().clear();
        xAxis.setCategories(FXCollections.observableArrayList(dates));

        XYChart.Series<String, Number> cancelled = new XYChart.Series<>();
        cancelled.setName(SERIES_CANCELLED);
        XYChart.Series<String, Number> noShow = new XYChart.Series<>();
        noShow.setName(SERIES_NO_SHOW);
        for (CancellationsReportRow r : rows) {
            cancelled.getData().add(new XYChart.Data<>(r.getDate(), r.getCancelled()));
            noShow.getData().add(new XYChart.Data<>(r.getDate(), r.getNoShow()));
        }
        chart.getData().add(cancelled);
        chart.getData().add(noShow);

        summaryLabel.setText("Total cancelled: " + report.getTotalCancelled()
                + "      ·      Total no-show: " + report.getTotalNoShow()
                + "      ·      Average per day: " + String.format("%.2f", report.getAvgPerDay()));
        showPlaceholder(false);
    }

    /** Swaps between the chart (with its summary line) and the empty-range placeholder. */
    private void showPlaceholder(boolean empty) {
        chart.setVisible(!empty);
        chart.setManaged(!empty);
        summaryLabel.setVisible(!empty);
        summaryLabel.setManaged(!empty);
        placeholderBox.setVisible(empty);
        placeholderBox.setManaged(empty);
    }
}
