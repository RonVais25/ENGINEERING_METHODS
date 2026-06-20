package client.view;

import client.app.Session;
import client.service.NetworkService;
import common.dto.NotificationDTO;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.ArrayList;
import java.util.List;

/**
 * Notification center, reachable from the sidebar by any logged-in actor. On show
 * it loads the actor's notifications via {@code LIST_NOTIFICATIONS} (the server
 * derives the recipient from the session) and lists them newest-first, with unread
 * messages highlighted and offering an Acknowledge action.
 *
 * <p>This is the <em>offline-fetch</em> half of the feature: a notification sent
 * while the actor was offline waits in the table and shows up here on next login;
 * a notification sent while they are online also arrives as an instant popup (wired
 * in {@link MainShellController}). Acknowledge sends {@code ACK_NOTIFICATION} and,
 * on success, reloads so the row drops out of the unread set.
 *
 * <p>Extends {@link BaseController} for navigation-lifecycle parity. It holds no
 * push subscription of its own — the shell owns the session-lifetime subscription
 * to {@code ("notification", actorId)}, so this screen must not SUBSCRIBE/UNSUBSCRIBE
 * that key (a per-screen UNSUBSCRIBE on hide would tear down the shell's popup feed,
 * since the server tracks subscriptions per connection, not per callback).
 */
public class NotificationCenterController extends BaseController {
/** Stores the refresh btn value used by this component. */

    @FXML private Button refreshBtn;
    @FXML private Label  resultLabel;
    @FXML private Label  cardHeaderLabel;
    @FXML private VBox   tableBox;

    // The (NetworkService, Session) shape is what the Navigator's controller
    // factory injects; this screen needs only the network (the server derives the
    // recipient from the session), so the session is accepted but unused.
/**
 * Creates a new notification center controller instance.
 * @param network value supplied to the operation
 * @param session value supplied to the operation
 */
    public NotificationCenterController(NetworkService network, Session session) {
        super(network);
    }
/**
 * Initializes the controller after its FXML fields are injected.
 */

    @FXML
    private void initialize() {
        load();
    }
/**
 * Performs the on refresh operation.
 */

    @FXML
    private void onRefresh() {
        load();
    }

    /** Loads the actor's notifications from the server and repaints the list. */
    private void load() {
        refreshBtn.setDisable(true);
        network.listNotifications().thenAccept(res -> {
            refreshBtn.setDisable(false);
            if (!res.isSuccess()) {
                Widgets.showToast(resultLabel, false, res.getMessage());
                return;
            }
            List<NotificationDTO> rows = new ArrayList<>();
            if (res.getData() instanceof List<?> raw) {
                for (Object o : raw) rows.add((NotificationDTO) o);
            }
            populate(rows);
        });
    }
/**
 * Performs the populate operation.
 * @param rows value supplied to the operation
 */

    private void populate(List<NotificationDTO> rows) {
        int unread = 0;
        for (NotificationDTO n : rows) {
            if (!n.isAcknowledged()) unread++;
        }
        cardHeaderLabel.setText("YOUR NOTIFICATIONS (" + rows.size() + " · " + unread + " unread)");
        tableBox.getChildren().clear();

        if (rows.isEmpty()) {
            Label none = new Label("No notifications yet.");
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
 * Performs the data row operation.
 * @param n value supplied to the operation
 * @param withDivider value supplied to the operation
 * @return the result produced by the operation
 */

    private HBox dataRow(NotificationDTO n, boolean withDivider) {
        boolean unread = !n.isAcknowledged();

        Label bodyLbl = new Label(n.getBody());
        bodyLbl.getStyleClass().add("notif-body");
        bodyLbl.setWrapText(true);

        Label metaLbl = new Label(n.getChannel() + " → " + n.getSimulatedTarget() + "   ·   " + when(n));
        metaLbl.getStyleClass().add("notif-meta");

        VBox textCol = new VBox(3, bodyLbl, metaLbl);
        HBox.setHgrow(textCol, Priority.ALWAYS);

        HBox row = new HBox(12, textCol);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("history-row");
        if (unread)      row.getStyleClass().add("unread");
        if (withDivider) row.getStyleClass().add("with-divider");

        if (unread) {
            Button ackBtn = new Button("Acknowledge");
            ackBtn.getStyleClass().add("btn-secondary");
            ackBtn.setOnAction(e -> acknowledge(n.getId()));
            row.getChildren().add(ackBtn);
        } else {
            Label read = new Label("✓ Read");
            read.getStyleClass().addAll("notif-meta", "muted");
            row.getChildren().add(read);
        }
        return row;
    }

    /** Acknowledges one notification, then reloads on success so it clears unread. */
    private void acknowledge(int notificationId) {
        network.ackNotification(notificationId).thenAccept(res -> {
            Widgets.showToast(resultLabel, res.isSuccess(), res.getMessage());
            if (res.isSuccess()) load();
        });
    }

    /** Prefers the delivery time, falling back to creation time; {@code ""} if neither is set. */
    private static String when(NotificationDTO n) {
        String t = n.getSentAt() != null ? n.getSentAt() : n.getCreatedAt();
        return t == null ? "" : t;
    }
}
