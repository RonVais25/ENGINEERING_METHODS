package client.view;

import client.app.Session;
import client.service.NetworkService;
import common.dto.ReservationDTO;
import common.dto.ReservationStatus;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.util.List;

/**
 * "My Reservations" screen: look up a visitor's reservations by id and manage
 * them. Each row offers Confirm / Cancel, enabled only for statuses where the
 * action is legal. The disabling is a UX hint — the server independently
 * enforces the legal transitions, so a stale or tampered client cannot drive an
 * illegal one.
 *
 * <p>Extends {@link BaseController} for navigation lifecycle parity; it does not
 * subscribe to push events (realtime reservation push is a later session).
 */
public class ReservationListController extends BaseController {

    @FXML private TextField visitorField;
    @FXML private Button    loadBtn;
    @FXML private Label     resultLabel;
    @FXML private Label     cardHeaderLabel;
    @FXML private VBox      tableBox;

    // The visitor whose list is currently shown, so action handlers can refresh
    // the same list after a successful confirm/cancel. -1 means "nothing loaded".
    private long currentVisitorId = -1;

    public ReservationListController(NetworkService network, Session session) {
        super(network);
    }

    @FXML
    private void initialize() {
        visitorField.setOnAction(e -> onLoad());
    }

    @FXML
    private void onLoad() {
        // TODO: use the logged-in visitor when Auth lands instead of a typed id.
        String raw = visitorField.getText() == null ? "" : visitorField.getText().trim();
        long visitorId;
        try {
            visitorId = Long.parseLong(raw);
        } catch (NumberFormatException ex) {
            Widgets.showToast(resultLabel, false, "Enter a valid numeric Visitor ID");
            return;
        }
        loadFor(visitorId);
    }

    private void loadFor(long visitorId) {
        currentVisitorId = visitorId;
        loadBtn.setDisable(true);
        network.listReservations(visitorId).thenAccept(res -> {
            loadBtn.setDisable(false);
            if (!res.isSuccess()) {
                Widgets.showToast(resultLabel, false, res.getMessage());
                return;
            }
            List<?> rows = (List<?>) res.getData();
            populate(rows == null ? List.of() : rows);
        });
    }

    private void populate(List<?> rows) {
        cardHeaderLabel.setText("RESERVATIONS — VISITOR " + currentVisitorId);
        tableBox.getChildren().setAll(headerRow());

        if (rows.isEmpty()) {
            Label none = new Label("No reservations for this visitor.");
            none.getStyleClass().addAll("history-cell", "muted");
            HBox row = new HBox(none);
            row.getStyleClass().add("history-row");
            tableBox.getChildren().add(row);
            return;
        }

        for (int i = 0; i < rows.size(); i++) {
            ReservationDTO r = (ReservationDTO) rows.get(i);
            tableBox.getChildren().add(dataRow(r, i < rows.size() - 1));
        }
    }

    private HBox headerRow() {
        HBox row = new HBox();
        row.getStyleClass().add("history-header-row");
        row.getChildren().addAll(
                headerCell("ID",      60),
                headerCell("DATE",   110),
                headerCell("PARTY",   70),
                headerCell("TYPE",   110),
                headerCell("STATUS", 110),
                headerCell("ACTIONS", 0));
        return row;
    }

    private HBox dataRow(ReservationDTO r, boolean withDivider) {
        Label idLbl    = cell("#" + r.getId(),                "num", 60);
        Label dateLbl  = cell(r.getVisitDate(),               null, 110);
        Label partyLbl = cell(String.valueOf(r.getPartySize()), null, 70);
        Label typeLbl  = cell(r.getVisitType().name(),        null, 110);

        Label statusTag = new Label(r.getStatus().name());
        statusTag.getStyleClass().addAll("status-tag", r.getStatus().name().toLowerCase());
        statusTag.setPrefWidth(110);

        Button confirmBtn = new Button("Confirm");
        confirmBtn.getStyleClass().add("btn-secondary");
        confirmBtn.setDisable(r.getStatus() != ReservationStatus.PENDING);
        confirmBtn.setOnAction(e -> confirm(r.getId()));

        Button cancelBtn = new Button("Cancel");
        cancelBtn.getStyleClass().add("btn-secondary");
        cancelBtn.setDisable(!canCancel(r.getStatus()));
        cancelBtn.setOnAction(e -> cancel(r.getId()));

        HBox actions = new HBox(8, confirmBtn, cancelBtn);
        actions.setAlignment(Pos.CENTER_LEFT);

        HBox row = new HBox(idLbl, dateLbl, partyLbl, typeLbl, statusTag, actions);
        row.getStyleClass().add("history-row");
        if (withDivider) row.getStyleClass().add("with-divider");
        return row;
    }

    /** Cancel is legal from PENDING, CONFIRMED or WAITING (mirrors the server rule). */
    private boolean canCancel(ReservationStatus s) {
        return s == ReservationStatus.PENDING
                || s == ReservationStatus.CONFIRMED
                || s == ReservationStatus.WAITING;
    }

    private void confirm(int reservationId) {
        network.confirmReservation(reservationId).thenAccept(res -> {
            Widgets.showToast(resultLabel, res.isSuccess(), res.getMessage());
            if (res.isSuccess()) reload();
        });
    }

    private void cancel(int reservationId) {
        network.cancelReservation(reservationId).thenAccept(res -> {
            Widgets.showToast(resultLabel, res.isSuccess(), res.getMessage());
            if (res.isSuccess()) reload();
        });
    }

    private void reload() {
        if (currentVisitorId >= 0) loadFor(currentVisitorId);
    }

    private Label headerCell(String text, double w) {
        Label l = new Label(text);
        l.getStyleClass().add("history-header-cell");
        if (w > 0) l.setPrefWidth(w);
        return l;
    }

    private Label cell(String text, String modifier, double w) {
        Label l = new Label(text);
        l.getStyleClass().add("history-cell");
        if (modifier != null) l.getStyleClass().add(modifier);
        l.setPrefWidth(w);
        return l;
    }
}
