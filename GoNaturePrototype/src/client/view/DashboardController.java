package client.view;

import client.app.Navigator;
import client.app.Session;
import client.service.NetworkService;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/**
 * Dashboard view: three stat cards over a "recent orders" list. The list is
 * static demo data (same as the original monolith) — clicking a row jumps to
 * the Get Order screen via the Navigator.
 */
public class DashboardController {

    @FXML private HBox statsRow;
    @FXML private VBox recentList;

    private final Navigator navigator;

    public DashboardController(NetworkService network, Session session, Navigator navigator) {
        this.navigator = navigator;
    }

    @FXML
    private void initialize() {
        statsRow.getChildren().addAll(
            statCard("ACTIVE ORDERS", "3",      "This month"),
            statCard("NEXT VISIT",    "Jun 15", "Order #1055"),
            statCard("SUBSCRIBER",    "#4821",  "Member since 2024")
        );

        Object[][] orders = {
            {1023, "Jun 12, 2026", 4, "Confirmed"},
            {1055, "Jun 15, 2026", 2, "Pending"},
            {1087, "Jun 28, 2026", 6, "Confirmed"},
        };
        for (Object[] o : orders) {
            HBox row = recentRow((int) o[0], (String) o[1], (int) o[2], (String) o[3]);
            row.setOnMouseClicked(e -> navigator.go("get"));
            recentList.getChildren().add(row);
        }
    }

    private VBox statCard(String label, String value, String sub) {
        VBox card = new VBox(
            withClass(new Label(label), "stat-card-label"),
            withClass(new Label(value), "stat-card-value"),
            withClass(new Label(sub),   "stat-card-sub")
        );
        card.getStyleClass().add("stat-card");
        HBox.setHgrow(card, Priority.ALWAYS);
        return card;
    }

    private HBox recentRow(int num, String date, int visitors, String status) {
        Region dot = new Region();
        dot.getStyleClass().addAll("dot", status.equals("Confirmed") ? "ok" : "pending");

        Label name    = withClass(new Label("Order #" + num),             "recent-row-name");
        Label details = withClass(new Label(date + " · " + visitors + " visitors"), "recent-row-details");
        VBox info = new VBox(2, name, details);

        HBox left = new HBox(10, dot, info);
        left.setAlignment(Pos.CENTER_LEFT);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Label tag = new Label(status);
        tag.getStyleClass().addAll("status-tag", status.toLowerCase());

        HBox row = new HBox(left, spacer, tag);
        row.getStyleClass().add("recent-row");
        return row;
    }

    private static <T extends javafx.scene.Node> T withClass(T node, String cls) {
        node.getStyleClass().add(cls);
        return node;
    }
}
