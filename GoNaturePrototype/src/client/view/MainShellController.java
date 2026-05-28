package client.view;

import client.app.Navigator;
import client.app.Navigator.Screen;
import client.app.Session;
import client.service.NetworkService;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.util.List;

/**
 * Controls the persistent shell: sidebar nav, topbar title, connection pill,
 * and the contentArea swap point. All screen-specific behaviour lives in the
 * per-screen controllers that {@link Navigator} loads into contentArea.
 */
public class MainShellController {

    @FXML private VBox      navBox;
    @FXML private StackPane contentArea;
    @FXML private Label     topbarTitle;
    @FXML private Label     topbarSubtitle;
    @FXML private Region    connDot;
    @FXML private Label     connHostLbl;
    @FXML private Label     connStatusLbl;
    @FXML private Label     userInitialsLbl;
    @FXML private Label     userNameLbl;
    @FXML private Label     userIdLbl;

    private final NetworkService network;
    private final Session session;

    public MainShellController(NetworkService network, Session session) {
        this.network = network;
        this.session = session;
    }

    @FXML
    private void initialize() {
        Navigator navigator = new Navigator(contentArea, network, session, this::onScreenChange);

        List<Screen> screens = List.of(
            new Screen("dashboard", "⊞", "Dashboard",    "/client/view/DashboardView.fxml",
                       "Dashboard",    "Welcome back"),
            new Screen("get",       "⊕", "Get Order",    "/client/view/GetOrderView.fxml",
                       "Get Order",    "Look up an existing order"),
            new Screen("update",    "✎", "Update Order", "/client/view/UpdateOrderView.fxml",
                       "Update Order", "Modify order details"),
            new Screen("new",       "+",      "New Booking",  "/client/view/NewBookingView.fxml",
                       "New Booking",  "Reserve your next park visit"),
            new Screen("history",   "☰", "History",      "/client/view/HistoryView.fxml",
                       "History",      "All your past orders")
        );
        for (Screen s : screens) {
            navigator.register(s);
            Button btn = Navigator.buildNavButton(s.icon(), s.label());
            navBox.getChildren().add(btn);
            navigator.bindNavButton(s.id(), btn);
        }

        userInitialsLbl.setText(session.getUserInitials());
        userNameLbl.setText(session.getUserName());
        userIdLbl.setText("Subscriber #" + session.getSubscriberId());
        connHostLbl.setText(session.getHost() + ":" + session.getPort());
        setConnDotOk(true);

        network.addConnectionListener(this::updateConnStatus);

        navigator.go("dashboard");
    }

    private void onScreenChange(Screen s) {
        topbarTitle.setText(s.title());
        topbarSubtitle.setText(s.subtitle());
    }

    private void updateConnStatus(boolean ok) {
        setConnDotOk(ok);
        connStatusLbl.setText(ok ? "Connected" : "Disconnected");
    }

    private void setConnDotOk(boolean ok) {
        connDot.getStyleClass().removeAll("err");
        if (!ok) connDot.getStyleClass().add("err");
    }
}
