package client.view;

import javafx.application.Platform;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
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

    /** Non-instantiable: all members are static. */
    private Widgets() {}

    /* ---------- Toast ----------------------------------------------------- */

    /**
     * A blank invisible label, marked .toast — call {@link #showToast} to use.
     *
     * @return a hidden, unmanaged toast {@link Label}
     */
    public static Label buildToastLabel() {
        Label l = new Label();
        l.getStyleClass().add("toast");
        l.setVisible(false);
        l.setManaged(false);
        l.setWrapText(true);
        return l;
    }

    /**
     * Show the toast with success/error styling, then auto-hide after 3 s.
     *
     * @param toast the toast label to show
     * @param ok    {@code true} for success styling, {@code false} for error
     * @param msg   the message to display
     */
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

    /* ---------- Activity log --------------------------------------------- */
    /**
     * Creates an empty activity log container.
     *
     * @return activity log VBox
     */
    public static VBox buildLogBox() {
        VBox box = new VBox(4);
        Label empty = new Label("No activity yet…");
        empty.getStyleClass().add("log-empty");
        box.getChildren().add(empty);
        return box;
    }
    
    /**
     * Wraps the activity log in a scrollable pane.
     *
     * @param logBox activity log container
     * @return scroll pane containing the log
     */
    public static ScrollPane wrapLog(VBox logBox) {
        ScrollPane scroll = new ScrollPane(logBox);
        scroll.setFitToWidth(true);
        scroll.getStyleClass().add("log-scroll");
        return scroll;
    }
    
    /**
     * Adds a new entry to the activity log.
     *
     * @param logBox activity log container
     * @param ok indicates whether the action succeeded
     * @param msg log message
     */
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
}
