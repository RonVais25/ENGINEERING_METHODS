package client.view;

import common.dto.OrderDTO;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/**
 * Reusable view fragments shared by multiple screen controllers.
 *
 * Everything here delegates styling to client.css via styleClass — no
 * setStyle() calls. These widgets exist as Java helpers (instead of FXML
 * fragments) because their content is highly dynamic and built from data.
 */
public final class Widgets {

    private Widgets() {}

    /* ---------- Toast ----------------------------------------------------- */

    /** A blank invisible label, marked .toast — call {@link #showToast} to use. */
    public static Label buildToastLabel() {
        Label l = new Label();
        l.getStyleClass().add("toast");
        l.setVisible(false);
        l.setManaged(false);
        l.setWrapText(true);
        return l;
    }

    /** Show the toast with success/error styling, then auto-hide after 3 s. */
    public static void showToast(Label toast, boolean ok, String msg) {
        toast.setText((ok ? "✓ " : "✕ ") + msg);
        toast.getStyleClass().removeAll("success", "error");
        toast.getStyleClass().add(ok ? "success" : "error");
        toast.setVisible(true);
        toast.setManaged(true);

        Thread t = new Thread(() -> {
            try { Thread.sleep(3000); } catch (InterruptedException ignored) {}
            Platform.runLater(() -> {
                toast.setVisible(false);
                toast.setManaged(false);
            });
        });
        t.setDaemon(true);
        t.start();
    }

    /* ---------- Result panel --------------------------------------------- */

    /** Build the empty-state result panel. */
    public static VBox buildResultPanel() {
        VBox panel = new VBox(20);
        showEmptyResultPanel(panel);
        return panel;
    }

    public static void showEmptyResultPanel(VBox panel) {
        panel.getChildren().clear();

        Label title = new Label("Order Details");
        title.getStyleClass().add("result-title");

        Label icon = new Label("🌿");
        icon.getStyleClass().add("result-empty-icon");
        Label msg  = new Label("No order loaded");
        msg.getStyleClass().add("result-empty-msg");
        Label hint = new Label("Enter an order number above");
        hint.getStyleClass().add("result-empty-hint");

        VBox empty = new VBox(icon, msg, hint);
        empty.getStyleClass().add("result-empty");
        empty.setAlignment(Pos.CENTER);

        panel.getChildren().addAll(title, empty);
    }

    public static void clearResultPanel(VBox panel) { showEmptyResultPanel(panel); }

    public static void populateResultPanel(VBox panel, OrderDTO dto) {
        panel.getChildren().clear();

        Label title = new Label("Order Details");
        title.getStyleClass().add("result-title");

        // Header
        Label orderLbl = new Label("ORDER NUMBER");
        orderLbl.getStyleClass().add("order-label");
        Label orderNum = new Label("#" + dto.getOrderNumber());
        orderNum.getStyleClass().add("order-num");
        VBox orderInfo = new VBox(2, orderLbl, orderNum);

        Region hSpacer = new Region();
        HBox.setHgrow(hSpacer, Priority.ALWAYS);

        Label statusTag = new Label("Confirmed");
        statusTag.getStyleClass().addAll("status-tag", "on-dark");

        HBox header = new HBox(orderInfo, hSpacer, statusTag);
        header.getStyleClass().add("result-card-header");

        // Body
        VBox body = new VBox();
        body.getStyleClass().add("result-card-body");

        String[][] rows = {
            {"Visit Date",    dto.getOrderDate()},
            {"Visitors",      String.valueOf(dto.getNumberOfVisitors())},
            {"Status",        "Confirmed"},
            {"Confirmation",  String.valueOf(dto.getConfirmationCode())},
            {"Subscriber ID", String.valueOf(dto.getSubscriberId())},
            {"Order Placed",  dto.getDateOfPlacingOrder()},
        };
        for (int i = 0; i < rows.length; i++) {
            Label key = new Label(rows[i][0]);
            key.getStyleClass().add("key");
            Label val = new Label(rows[i][1]);
            val.getStyleClass().add("val");
            if (rows[i][0].equals("Visit Date")) val.getStyleClass().add("date");

            HBox row = new HBox(key, val);
            row.getStyleClass().add("result-row");
            if (i > 0) row.getStyleClass().add("with-divider");
            body.getChildren().add(row);
        }

        VBox card = new VBox(header, body);
        card.getStyleClass().add("result-card");

        panel.getChildren().addAll(title, card);
    }

    /* ---------- Activity log --------------------------------------------- */

    public static VBox buildLogBox() {
        VBox box = new VBox(4);
        Label empty = new Label("No activity yet…");
        empty.getStyleClass().add("log-empty");
        box.getChildren().add(empty);
        return box;
    }

    public static ScrollPane wrapLog(VBox logBox) {
        ScrollPane scroll = new ScrollPane(logBox);
        scroll.setFitToWidth(true);
        scroll.getStyleClass().add("log-scroll");
        return scroll;
    }

    public static void addLog(VBox logBox, boolean ok, String msg) {
        if (!logBox.getChildren().isEmpty()
            && logBox.getChildren().get(0) instanceof Label l
            && l.getStyleClass().contains("log-empty")) {
            logBox.getChildren().clear();
        }
        String time = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
        Label entry = new Label(time + "  " + msg);
        entry.getStyleClass().addAll("log-entry", ok ? "ok" : "err");
        entry.setWrapText(true);
        logBox.getChildren().add(0, entry);
    }

    /* ---------- Step indicator (used by Update Order) -------------------- */

    public static HBox buildStepIndicator(int activeIndex) {
        String[] labels = {"Find Order", "Edit Details", "Confirm"};
        HBox row = new HBox();
        row.getStyleClass().add("step-row");

        for (int i = 0; i < 3; i++) {
            Label num = new Label(i < activeIndex ? "✓" : String.valueOf(i + 1));
            num.getStyleClass().add("step-num");

            VBox circle = new VBox(num);
            circle.getStyleClass().add("step-circle");
            circle.setAlignment(Pos.CENTER);
            if (i < activeIndex)       circle.getStyleClass().add("done");
            else if (i == activeIndex) circle.getStyleClass().add("active");

            Label lbl = new Label(labels[i]);
            lbl.getStyleClass().add("step-label");

            HBox piece = new HBox(8, circle, lbl);
            piece.getStyleClass().add("step-piece");
            if (i == activeIndex) piece.getStyleClass().add("active");
            piece.setAlignment(Pos.CENTER_LEFT);
            row.getChildren().add(piece);

            if (i < 2) {
                Region line = new Region();
                line.getStyleClass().add("step-line");
                if (i < activeIndex) line.getStyleClass().add("done");
                row.getChildren().add(line);
            }
        }
        return row;
    }
}
