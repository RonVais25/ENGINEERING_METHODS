package client.view;

import client.app.Session;
import client.service.NetworkService;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

/**
 * History view: a static-data table of past orders. Rows are programmatic
 * (data-driven) but every cell uses CSS classes from client.css for styling.
 */
public class HistoryController {

    @FXML private Label cardHeaderLabel;
    @FXML private Label footerLabel;
    @FXML private VBox  tableBox;

    private final Session session;

    public HistoryController(NetworkService network, Session session) {
        this.session = session;
    }

    @FXML
    private void initialize() {
        cardHeaderLabel.setText("ALL ORDERS — SUBSCRIBER #" + session.getSubscriberId());
        footerLabel.setText("Showing 3 orders · Connected to " + session.getHost() + ":" + session.getPort());

        tableBox.getChildren().add(headerRow());

        Object[][] data = {
            {1087, "Jun 28, 2026", 6, "CONF-2891", "Confirmed", "May 2, 2026"},
            {1055, "Jun 15, 2026", 2, "CONF-1774", "Pending",   "Apr 28, 2026"},
            {1023, "Jun 12, 2026", 4, "CONF-0392", "Confirmed", "Apr 20, 2026"},
        };
        for (int i = 0; i < data.length; i++) {
            Object[] r = data[i];
            boolean withDivider = i < data.length - 1;
            tableBox.getChildren().add(dataRow(
                (int) r[0], (String) r[1], (int) r[2],
                (String) r[3], (String) r[4], (String) r[5], withDivider));
        }
    }

    private HBox headerRow() {
        HBox row = new HBox();
        row.getStyleClass().add("history-header-row");
        row.getChildren().addAll(
            headerCell("ORDER #",      80),
            headerCell("VISIT DATE",  110),
            headerCell("VISITORS",     90),
            headerCell("CONFIRMATION",110),
            headerCell("STATUS",       90),
            headerCell("PLACED",      110)
        );
        return row;
    }

    private Label headerCell(String text, double w) {
        Label l = new Label(text);
        l.getStyleClass().add("history-header-cell");
        l.setPrefWidth(w);
        return l;
    }

    private HBox dataRow(int num, String date, int visitors, String conf,
                         String status, String placed, boolean withDivider) {
        Label numLbl    = cell("#" + num,                 "num",   80);
        Label dateLbl   = cell(date,                      null,   110);
        Label visitLbl  = cell(String.valueOf(visitors),  null,    90);
        Label confLbl   = cell(conf,                      "mono", 110);
        Label statusTag = new Label(status);
        statusTag.getStyleClass().addAll("status-tag", status.toLowerCase());
        statusTag.setPrefWidth(90);
        Label placedLbl = cell(placed, "muted", 110);

        HBox row = new HBox(numLbl, dateLbl, visitLbl, confLbl, statusTag, placedLbl);
        row.getStyleClass().add("history-row");
        if (withDivider) row.getStyleClass().add("with-divider");
        return row;
    }

    private Label cell(String text, String modifier, double w) {
        Label l = new Label(text);
        l.getStyleClass().add("history-cell");
        if (modifier != null) l.getStyleClass().add(modifier);
        l.setPrefWidth(w);
        return l;
    }
}
