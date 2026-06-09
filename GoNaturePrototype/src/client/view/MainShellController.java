package client.view;

import client.app.Navigator;
import client.app.Navigator.Screen;
import client.app.Session;
import client.service.NetworkService;
import common.dto.Role;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

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
 * also unreachable (not just invisible). Visitors see the reservation screens;
 * staff additionally see the order tools; the {@code MANAGER_ONLY} predicate is
 * wired and ready for future manager screens (e.g. parameter approvals) to slot
 * into {@link #navItems()}.
 */
public class MainShellController {

    /** One sidebar entry: the screen plus the rule deciding who may see it. */
    private record NavItem(Screen screen, Predicate<Session> visibleWhen) {}

    // Visibility predicates — drive gating off the live Session, never hardcoded.
    private static final Predicate<Session> EVERYONE     = s -> true;
    private static final Predicate<Session> STAFF_ONLY   = Session::isStaff;
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
            // Order tools — staff only (legacy order desk).
            // TODO: retire the legacy Order feature entirely once reservations
            // cover every flow — delete these four screens + their controllers/
            // FXML, the Order DTO/DAO/controller path, and Session.getSubscriberId().
            new NavItem(new Screen("get", "⊕", "Get Order", "/client/view/GetOrderView.fxml",
                        "Get Order", "Look up an existing order"), STAFF_ONLY),
            new NavItem(new Screen("update", "✎", "Update Order", "/client/view/UpdateOrderView.fxml",
                        "Update Order", "Modify order details"), STAFF_ONLY),
            new NavItem(new Screen("new", "+", "New Booking", "/client/view/NewBookingView.fxml",
                        "New Booking", "Reserve your next park visit"), STAFF_ONLY),
            new NavItem(new Screen("history", "☰", "History", "/client/view/HistoryView.fxml",
                        "History", "All your past orders"), STAFF_ONLY),
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
                        "Approvals", "Review pending parameter-change requests"), DEPT_MANAGER_ONLY)
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
        session.clearIdentity();
        onLogout.run();
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
