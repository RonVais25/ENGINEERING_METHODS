package client.view;

import client.app.Navigator;
import client.app.Navigator.Screen;
import client.app.Session;
import client.net.EventBus;
import client.service.NetworkService;
import common.dto.ClientRequest;
import common.dto.NotificationDTO;
import common.dto.RequestType;
import common.dto.Role;
import common.dto.ServerEvent;
import common.dto.SubscriptionKey;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.stage.Window;

import java.util.List;
import java.util.function.Predicate;

/**
 * Controls the persistent shell: sidebar nav, topbar title, connection pill,
 * the logged-in user chrome, and the contentArea swap point. All screen-specific
 * behaviour lives in the per-screen controllers that {@link Navigator} loads.
 *
 * Role Aware: The sidebar is built from {@link #navItems()},
 * each entry carrying a {@link Predicate} over the {@link Session} that decides
 * whether the current identity may see it. Only the screens that pass are
 * registered with the {@link Navigator} and given a button, so a hidden screen is
 * also unreachable (not just invisible). Visitors and staff share the reservation
 * screens; role-specific predicates ({@code SERVICE_REP_ONLY},
 * {@code PARK_EMPLOYEE_ONLY}, {@code DEPT_MANAGER_ONLY}, …) gate the staff tools.
 */
public class MainShellController {

    /** One sidebar entry: the screen plus the rule deciding who may see it. */
    private record NavItem(Screen screen, Predicate<Session> visibleWhen) {}

    // Visibility predicates — drive gating off the live Session, never hardcoded.
    private static final Predicate<Session> EVERYONE     = s -> true;
    private static final Predicate<Session> VISITOR_ONLY = Session::isVisitor;
    private static final Predicate<Session> MANAGER_ONLY =
            s -> s.isStaff() && (s.getRole() == Role.PARK_MANAGER || s.getRole() == Role.DEPT_MANAGER);
    private static final Predicate<Session> SERVICE_REP_ONLY =
            s -> s.isStaff() && s.getRole() == Role.SERVICE_REP;
    private static final Predicate<Session> PARK_MANAGER_ONLY =
            s -> s.isStaff() && s.getRole() == Role.PARK_MANAGER;
    private static final Predicate<Session> DEPT_MANAGER_ONLY =
            s -> s.isStaff() && s.getRole() == Role.DEPT_MANAGER;
    private static final Predicate<Session> PARK_EMPLOYEE_ONLY =
            s -> s.isStaff() && s.getRole() == Role.PARK_EMPLOYEE;

    // TODO: anchor the sidebar (logo + user/login chrome) so it stays fixed while
    // the screen content area scrolls independently.
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
    @FXML private Button    logoutBtn;

    private final NetworkService network;
    private final Session        session;
    private final Runnable       onLogout;

    public MainShellController(NetworkService network, Session session, Runnable onLogout) {
        this.network  = network;
        this.session  = session;
        this.onLogout = onLogout;
    }

    @FXML
    private void initialize() {
        Navigator navigator = new Navigator(contentArea, network, session, this::onScreenChange);

        // Register + build buttons only for screens this identity may see.
        String firstId = null;
        for (NavItem item : navItems()) {
            if (!item.visibleWhen().test(session)) continue;
            Screen s = item.screen();
            navigator.register(s);
            Button btn = Navigator.buildNavButton(s.icon(), s.label());
            navBox.getChildren().add(btn);
            navigator.bindNavButton(s.id(), btn);
            if (firstId == null) firstId = s.id();
        }

        // User chrome: real identity (name + role), not a placeholder.
        userInitialsLbl.setText(initialsOf(session.getDisplayName()));
        userNameLbl.setText(session.getDisplayName());
        userIdLbl.setText(session.getRoleLabel());
        connHostLbl.setText(session.getHost() + ":" + session.getPort());
        setConnDotOk(true);

        network.addConnectionListener(this::updateConnStatus);

        // Subscribe to our own notifications for the lifetime of this login, so a
        // notification sent while we're online surfaces as an instant popup.
        subscribeToNotifications();

        if (firstId != null) navigator.go(firstId);
    }

    /**
     * The full catalogue of shell screens with their visibility rules. Order here
     * is the sidebar order. Future screens slot in with the appropriate predicate;
     * non-existent screens simply aren't listed yet.
     */
    private List<NavItem> navItems() {
        return List.of(
            new NavItem(new Screen("dashboard", "⊞", "Dashboard", "/client/view/DashboardView.fxml",
                        "Dashboard", "Welcome back"), EVERYONE),
            // Reservations — available to staff and visitors alike.
            new NavItem(new Screen("reserve", "✦", "Book Visit", "/client/view/ReservationCreateView.fxml",
                        "Book Visit", "Reserve a park visit (reservations)"), EVERYONE),
            new NavItem(new Screen("myres", "☑", "My Reservations", "/client/view/ReservationListView.fxml",
                        "My Reservations", "View, confirm or cancel your reservations"), EVERYONE),
            // Waiting list — visitor-facing: claim a freed slot when a park was full.
            new NavItem(new Screen("waitlist", "⏳", "Waiting List", "/client/view/WaitlistView.fxml",
                        "Waiting List", "Claim a freed slot when a park was full"), VISITOR_ONLY),
            // Notification center — every logged-in actor can review their messages.
            new NavItem(new Screen("notifications", "🔔", "Notifications", "/client/view/NotificationCenterView.fxml",
                        "Notifications", "Messages addressed to you"), EVERYONE),
            // Member registration — service reps only.
            new NavItem(new Screen("regsub", "★", "Register Subscriber", "/client/view/SubscriberRegisterView.fxml",
                        "Register Subscriber", "Sign up a new subscriber (members earn a discount)"), SERVICE_REP_ONLY),
            new NavItem(new Screen("regguide", "✚", "Register Guide", "/client/view/GuideRegisterView.fxml",
                        "Register Guide", "Register a visitor as a group guide"), SERVICE_REP_ONLY),
            // Gate — front-line park employee's entry/exit/walk-in tool.
            new NavItem(new Screen("gate", "⇄", "Gate", "/client/view/GateView.fxml",
                        "Gate", "Park entry, exit & casual walk-ins"), PARK_EMPLOYEE_ONLY),
            // Manager-only park screens — gated to a single role each so a hidden
            // screen is also unreachable (not registered with the Navigator).
            new NavItem(new Screen("parkparams", "⚙", "Park Settings",
                        "/client/view/ParkParamsView.fxml",
                        "Park Settings", "Request changes to your park's parameters"), PARK_MANAGER_ONLY),
            new NavItem(new Screen("approvals", "✓", "Approvals",
                        "/client/view/ApprovalQueueView.fxml",
                        "Approvals", "Review pending parameter-change requests"), DEPT_MANAGER_ONLY),
            // Department-manager reports — visits-by-type (chart) and cancellations (table).
            new NavItem(new Screen("visitsreport", "📊", "Visits Report",
                        "/client/view/VisitsReportView.fxml",
                        "Visits Report", "Visits by type — individuals vs organized groups"), DEPT_MANAGER_ONLY),
            new NavItem(new Screen("cancelreport", "📉", "Cancellations Report",
                        "/client/view/CancellationsReportView.fxml",
                        "Cancellations Report", "Cancellations & no-shows by day"), DEPT_MANAGER_ONLY)
        );
    }

    @FXML
    private void onLogout() {
        logoutBtn.setDisable(true);
        // Tell the server to release the single-login lock, then drop our local
        // identity and return to the login screen regardless of the reply — a
        // failed/again-disconnected logout must not strand the user in the shell.
        network.logout().thenAccept(res -> finishLogout());
    }

    private void finishLogout() {
        // Drop our notification subscription before the identity is cleared so the
        // server stops pushing to this connection and the next login starts clean.
        unsubscribeFromNotifications();
        session.clearIdentity();
        onLogout.run();
    }

    /* ---------- Notifications (session-lifetime push + popup) -------------- */

    /** Local handle for the logged-in actor's notification subscription, dropped on logout. */
    private EventBus.Subscription notifSub;
    /** The key {@link #notifSub} is registered against, retained so logout can UNSUBSCRIBE it. */
    private SubscriptionKey notifKey;

    /**
     * Registers interest in the logged-in actor's own notifications. Sends a
     * fire-and-forget SUBSCRIBE for {@code ("notification", actorId)} and attaches
     * a local {@link EventBus} callback. Unlike per-screen {@link BaseController}
     * subscriptions, this one lives for the whole login (across screen swaps), so
     * it is managed here in the shell rather than drained on navigation.
     */
    private void subscribeToNotifications() {
        long actorId = session.getActorId();
        if (actorId < 0) return;

        notifKey = new SubscriptionKey("notification", actorId);
        ClientRequest req = new ClientRequest(RequestType.SUBSCRIBE);
        req.put("entity",   "notification");
        req.put("entityId", actorId);
        network.send(req); // fire-and-forget; same pattern as BaseController.subscribe

        notifSub = EventBus.getInstance().subscribe(notifKey, this::onNotificationEvent);
    }

    /** Detaches the notification subscription and tells the server to stop pushing. */
    private void unsubscribeFromNotifications() {
        if (notifSub == null) return;
        ClientRequest req = new ClientRequest(RequestType.UNSUBSCRIBE);
        req.put("entity",   "notification");
        req.put("entityId", notifKey.entityId());
        network.send(req);
        notifSub.unsubscribe();
        notifSub = null;
        notifKey = null;
    }

    /**
     * Handles a pushed notification event (already marshalled onto the FX thread by
     * {@link EventBus}). Shows the simulation popup for the carried
     * {@link NotificationDTO}; ignores events without the expected payload.
     */
    private void onNotificationEvent(ServerEvent ev) {
        if (ev.getPayload() instanceof NotificationDTO n) {
            showNotificationPopup(n);
        }
    }

    /**
     * Shows a non-blocking, auto-dismissing popup with the simulation text
     * <em>"Simulation — would send via &lt;channel&gt; to &lt;target&gt;: &lt;body&gt;"</em>.
     *
     * <p>Deliberately uses a non-modal, undecorated {@link Stage} (not
     * {@code Alert.showAndWait()}) so it never freezes the FX event loop or blocks
     * interaction with the shell. A daemon timer closes it after a few seconds.
     *
     * @param n the notification to render
     */
    private void showNotificationPopup(NotificationDTO n) {
        String text = "Simulation — would send via " + n.getChannel()
                + " to " + n.getSimulatedTarget() + ": " + n.getBody();

        Label title = new Label("🔔 Notification");
        title.getStyleClass().add("notif-popup-title");

        Label body = new Label(text);
        body.getStyleClass().add("notif-popup-body");
        body.setWrapText(true);
        body.setMaxWidth(300);

        Button dismiss = new Button("Dismiss");
        dismiss.getStyleClass().add("btn-secondary");

        VBox box = new VBox(10, title, body, dismiss);
        box.getStyleClass().add("notif-popup");

        Stage popup = new Stage();
        popup.initStyle(StageStyle.UNDECORATED);
        popup.initModality(Modality.NONE);

        Scene shellScene = contentArea.getScene();
        Window owner = shellScene != null ? shellScene.getWindow() : null;
        if (owner != null) popup.initOwner(owner);

        Scene scene = new Scene(box);
        if (shellScene != null) scene.getStylesheets().addAll(shellScene.getStylesheets());
        popup.setScene(scene);

        dismiss.setOnAction(e -> popup.close());
        // Anchor bottom-right of the shell window once the popup has been sized.
        popup.setOnShown(e -> {
            if (owner != null) {
                popup.setX(owner.getX() + owner.getWidth()  - popup.getWidth()  - 24);
                popup.setY(owner.getY() + owner.getHeight() - popup.getHeight() - 24);
            }
        });
        popup.show();

        Thread t = new Thread(() -> {
            try { Thread.sleep(8000); } catch (InterruptedException ignored) {}
            Platform.runLater(() -> { if (popup.isShowing()) popup.close(); });
        });
        t.setDaemon(true);
        t.start();
    }

    /** First letters of the first two name tokens, e.g. "Dana Department" → "DD". */
    private String initialsOf(String name) {
        if (name == null || name.isBlank()) return "?";
        String[] parts = name.trim().split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.length && sb.length() < 2; i++) {
            if (!parts[i].isEmpty()) sb.append(Character.toUpperCase(parts[i].charAt(0)));
        }
        return sb.length() == 0 ? "?" : sb.toString();
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
