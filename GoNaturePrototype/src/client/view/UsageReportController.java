package client.view;

import client.app.Session;
import client.service.NetworkService;
import common.dto.ParkDTO;
import common.dto.UsageReportDTO;
import common.dto.UsageReportRow;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Usage report screen. It shows the required operational information: dates and
 * parks in which the park was not fully occupied during the selected range.
 */
public class UsageReportController extends BaseController {

    /** Park dropdown entry: id is null for all parks. */
    private record ParkOption(Integer id, String name) {
        @Override public String toString() { return name; }
    }

    @FXML private DatePicker fromPicker;
    @FXML private DatePicker toPicker;
    @FXML private ComboBox<ParkOption> parkCombo;
    @FXML private Button generateBtn;
    @FXML private Label resultLabel;
    @FXML private Label cardHeaderLabel;
    @FXML private VBox tableBox;

    /**
     * Creates a usage report controller.
     *
     * @param network client network service
     * @param session current session, accepted for Navigator construction
     */
    public UsageReportController(NetworkService network, Session session) {
        super(network);
    }

    /** Initializes date defaults and loads the park dropdown. */
    @FXML
    private void initialize() {
        toPicker.setValue(LocalDate.now());
        fromPicker.setValue(LocalDate.now().minusMonths(1));
        loadParks();
    }

    /** Loads the park choices from the server. */
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
            parkCombo.getSelectionModel().select(0);
        });
    }

    /** Generates the report for the selected filter. */
    @FXML
    private void onGenerate() {
        LocalDate from = fromPicker.getValue();
        LocalDate to = toPicker.getValue();
        if (from == null || to == null) {
            Widgets.showToast(resultLabel, false, "Please choose both a From and To date");
            return;
        }
        if (to.isBefore(from)) {
            Widgets.showToast(resultLabel, false, "The To date must be on or after the From date");
            return;
        }

        ParkOption park = parkCombo.getValue();
        Integer parkId = park == null ? null : park.id();
        String fromStr = from.format(DateTimeFormatter.ISO_LOCAL_DATE);
        String toStr = to.format(DateTimeFormatter.ISO_LOCAL_DATE);

        generateBtn.setDisable(true);
        network.usageReport(fromStr, toStr, parkId).thenAccept(res -> {
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

    /** Renders a simple table of under-capacity park/day rows. */
    private void render(UsageReportDTO report) {
        tableBox.getChildren().clear();
        tableBox.getChildren().add(headerRow());
        List<UsageReportRow> rows = report.getRows();
        cardHeaderLabel.setText("USAGE REPORT — " + rows.size()
                + (rows.size() == 1 ? " under-capacity day" : " under-capacity days"));

        if (rows.isEmpty()) {
            Label none = new Label("No under-capacity records were found in this range.");
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

    /** Header row for the table. */
    private HBox headerRow() {
        HBox row = new HBox();
        row.getStyleClass().add("history-header-row");
        row.getChildren().addAll(
                headerCell("DATE", 90),
                headerCell("PARK", 150),
                headerCell("VISITORS", 80),
                headerCell("CAPACITY", 80),
                headerCell("UNUSED", 80),
                flexSpacer());
        return row;
    }

    /** Data row for one usage result. */
    private HBox dataRow(UsageReportRow r, boolean withDivider) {
        HBox row = new HBox(
                cell(r.getDate(), 90),
                cell(r.getParkName(), 150),
                cell(String.valueOf(r.getTotalVisitors()), 80),
                cell(String.valueOf(r.getMaxCapacity()), 80),
                cell(String.valueOf(r.getUnusedCapacity()), 80),
                flexSpacer());
        row.getStyleClass().add("history-row");
        if (withDivider) row.getStyleClass().add("with-divider");
        return row;
    }

    /** Styled header cell. */
    private Label headerCell(String text, double width) {
        Label label = new Label(text);
        label.getStyleClass().add("history-header-cell");
        label.setPrefWidth(width);
        return label;
    }

    /** Styled data cell. */
    private Label cell(String text, double width) {
        Label label = new Label(text);
        label.getStyleClass().add("history-cell");
        label.setPrefWidth(width);
        return label;
    }

    /** Flexible spacer for row alignment. */
    private Region flexSpacer() {
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        return spacer;
    }
}
