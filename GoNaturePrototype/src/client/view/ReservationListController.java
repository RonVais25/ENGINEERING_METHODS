package client.view;

import client.app.Session;
import client.service.NetworkService;
import common.dto.ReservationDTO;
import common.dto.ReservationStatus;
import common.dto.ServerEvent;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.util.ArrayList;
import java.util.List;

/**
 * "My Reservations" screen: look up a visitor's reservations by id and manage
 * them. Each row offers Confirm / Cancel, enabled only for statuses where the
 * action is legal. The disabling is a UX hint — the server independently
 * enforces the legal transitions, so a stale or tampered client cannot drive an
 * illegal one.
 *
 * <p>Realtime push: after each successful load the screen subscribes (through the
 * {@link BaseController#subscribe} helper) to every reservation id on screen, so a
 * confirm/cancel/update committed by another client refreshes the list within ~1s.
 * The push handler only re-reads the list (never a mutating op), so applying a
 * pushed change cannot loop back into another publish. {@link BaseController#onHide()}
 * auto-unsubscribes when the screen is navigated away.
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

    private final Session session;

    public ReservationListController(NetworkService network, Session session) {
        super(network);
        this.session = session;
    }

    @FXML
    private void initialize() {
        visitorField.setOnAction(e -> onLoad());

        // A logged-in visitor only sees their own reservations: prefill + lock
        // the id field and load it straight away. Staff leave it editable so they
        // can look up any visitor's list.
        if (session.isVisitor()) {
            visitorField.setText(String.valueOf(session.getActorId()));
            visitorField.setEditable(false);
            loadFor(session.getActorId());
        }
    }

    @FXML
    private void onLoad() {
        // Visitor's id is prefilled (and locked) above; staff type a visitor id here.
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
            List<ReservationDTO> rows = new ArrayList<>();
            if (res.getData() instanceof List<?> raw) {
                for (Object o : raw) rows.add((ReservationDTO) o);
            }
            populate(rows);
            resubscribe(rows);
        });
    }

    private void populate(List<ReservationDTO> rows) {
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
            tableBox.getChildren().add(dataRow(rows.get(i), i < rows.size() - 1));
        }
    }

    /**
     * Points this screen's realtime subscriptions at exactly the reservation ids
     * currently displayed. Drops the previous set first via the
     * {@link BaseController#unsubscribeAll()} helper (which also sends the matching
     * UNSUBSCRIBE), so a fresh Load or a push-driven refresh never leaks
     * subscriptions; {@link BaseController#onHide()} drops the rest on navigation.
     */
    private void resubscribe(List<ReservationDTO> rows) {
        unsubscribeAll();
        for (ReservationDTO r : rows) {
            subscribe("reservation", r.getId(), this::onReservationEvent);
        }
    }

    /**
     * Reacts to a pushed reservation change. Runs on the FX thread (the
     * {@code EventBus} marshals via {@code Platform.runLater}). It only re-reads
     * the visitor's list (LIST_RESERVATIONS is read-only and publishes nothing),
     * so applying a pushed change can never re-trigger a publish and loop.
     *
     * @param ev the pushed event (its id is one of the displayed reservations)
     */
    private void onReservationEvent(ServerEvent ev) {
        if (currentVisitorId >= 0) loadFor(currentVisitorId);
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

        // TODO: client.css only defines colours for the "confirmed" (green) and
        // "pending" (gold) status-tag modifiers, so CANCELLED/WAITING/COMPLETED/
        // NO_SHOW currently render as plain pills. Add matching rules
        // (.status-tag.cancelled -> red, .waiting, .completed, .no_show) in client.css.
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

        // TODO: let the visitor edit their own booking — add an "Edit" action that
        // sends UPDATE_RESERVATION (date / party size; server DAO already supports
        // it via updateDateAndParty) so they can change a reservation, not just
        // confirm/cancel it.
        HBox actions = new HBox(8, confirmBtn, cancelBtn);
        actions.setAlignment(Pos.CENTER_LEFT);

        // TODO: make the row clickable to open a read-only detail view (GET_RESERVATION
        // by id) showing the full reservation — time, price, confirmation code, guide,
        // waitlist position. Right now a visitor can list rows but can't drill into one.
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
