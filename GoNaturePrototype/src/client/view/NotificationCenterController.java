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

    /** Manually reloads the notifications. */
    @FXML private Button refreshBtn;
    /** Result/toast label for action feedback. */
    @FXML private Label  resultLabel;
    /** Header label showing total/unread counts. */
    @FXML private Label  cardHeaderLabel;
    /** Container the notification rows are rendered into. */
    @FXML private VBox   tableBox;

    // The (NetworkService, Session) shape is what the Navigator's controller
    // factory injects; this screen needs only the network (the server derives the
    // recipient from the session), so the session is accepted but unused.
    /**
     * Creates the notification-center controller.
     *
     * @param network the shared network service
     * @param session the current client session
     */
    public NotificationCenterController(NetworkService network, Session session) {
        super(network);
    }

    /** FXML lifecycle hook: loads the actor's notifications. */
    @FXML
    private void initialize() {
        load();
    }

    /** Refresh-button handler: reloads the notifications. */
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
     * Renders the header and one row per notification (or an empty-state row).
     *
     * @param rows the notifications to display, newest first
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
     * Builds one notification row (with an Acknowledge action when unread).
     *
     * @param n           the notification to render
     * @param withDivider whether to draw a divider below the row
     * @return the assembled row
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

    /**
     * Acknowledges one notification, then reloads on success so it clears unread.
     *
     * @param notificationId the notification to acknowledge
     */
    private void acknowledge(int notificationId) {
        network.ackNotification(notificationId).thenAccept(res -> {
            Widgets.showToast(resultLabel, res.isSuccess(), res.getMessage());
            if (res.isSuccess()) load();
        });
    }

    /**
     * Prefers the delivery time, falling back to creation time; {@code ""} if
     * neither is set.
     *
     * @param n the notification
     * @return the best available timestamp, or {@code ""}
     */
    private static String when(NotificationDTO n) {
        String t = n.getSentAt() != null ? n.getSentAt() : n.getCreatedAt();
        return t == null ? "" : t;
    }
}
