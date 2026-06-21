package client.view;

import client.app.Navigator;
import client.app.Session;
import client.service.NetworkService;
import common.dto.NotificationDTO;
import common.dto.OccupancyDTO;
import common.dto.ParkDTO;
import common.dto.ReservationDTO;
import common.dto.ReservationStatus;
import common.dto.Role;
import common.dto.ServerResponse;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Dashboard: a role-aware overview that greets the logged-in actor, shows a few
 * real summary cards plus an attention line, and offers a quick-link launchpad.
 *
 * <p>It is deliberately <em>not</em> another reservation list (that is "My
 * Reservations"): it summarizes and routes, never managing an individual item.
 * Every figure is read from a live server response through the existing
 * {@link NetworkService} wrappers — nothing is hardcoded:
 * <ul>
 *   <li><b>Visitor</b> — {@code listReservations} drives the next upcoming visit
 *       and the active count; {@code listNotifications} drives the unread count.</li>
 *   <li><b>PARK_EMPLOYEE</b> — {@code currentOccupancy} drives a live occupancy card.</li>
 *   <li><b>DEPT_MANAGER</b> — {@code listPendingChanges} drives a pending-approvals card.</li>
 *   <li><b>PARK_MANAGER</b> — {@code listParks} drives a capacity card (live occupancy is
 *       gated to PARK_EMPLOYEE server-side), plus the park-parameters link.</li>
 *   <li><b>SERVICE_REP</b> — registration quick links (no metric).</li>
 * </ul>
 *
 * <p>Park names are resolved via {@code listParks} (open to any logged-in actor)
 * so cards can name a park rather than show a bare id. All futures resolve on the
 * FX thread, so the populate callbacks update the scene graph directly without
 * blocking.
 */
public class DashboardController {

    /** Greeting line ("Welcome back, …"). */
    @FXML private Label greetingLbl;
    /** Pretty-printed role of the logged-in actor. */
    @FXML private Label roleLbl;
    /** Attention/alert banner line. */
    @FXML private Label alertLabel;
    /** Row holding the summary stat cards. */
    @FXML private HBox  statsRow;
    /** Row holding the quick-link buttons. */
    @FXML private HBox  quickLinks;

    /** Shared network service for the dashboard's metric calls. */
    private final NetworkService network;
    /** The current client session (identity and role). */
    private final Session        session;
    /** Navigator for the quick-link buttons. */
    private final Navigator      navigator;

    // Visitor attention counters: fed by two independent async calls
    // (reservations + notifications) and folded into the alert line whenever
    // either resolves. Both callbacks land on the FX thread, so there is no race.
    /** Count of the visitor's PENDING reservations (drives the alert line). */
    private int visitorPending;
    /** Count of the visitor's WAITING reservations (drives the alert line). */
    private int visitorWaiting;
    /** Count of the visitor's unread notifications (drives the alert line). */
    private int visitorUnread;

    /**
     * Creates the dashboard controller.
     *
     * @param network the shared network service
     * @param session the current client session
     * @param navigator navigates between screens
     */
    public DashboardController(NetworkService network, Session session, Navigator navigator) {
        this.network   = network;
        this.session   = session;
        this.navigator = navigator;
    }

    /** FXML lifecycle hook: greets the actor and renders the role-specific view. */
    @FXML
    private void initialize() {
        greetingLbl.setText("Welcome back, " + firstNameOf(session.getDisplayName()));
        roleLbl.setText(prettyRole(session.getRoleLabel()));

        if (!session.isStaff()) {
            renderVisitor();
            return;
        }
        Role role = session.getRole();
        if (role == null) return;
        switch (role) {
            case PARK_EMPLOYEE -> renderParkEmployee();
            case DEPT_MANAGER  -> renderDeptManager();
            case PARK_MANAGER  -> renderParkManager();
            case SERVICE_REP   -> renderServiceRep();
        }
    }

    /* ---------- Visitor ---------------------------------------------------- */

    /** Renders the visitor dashboard: next-visit and active-reservations cards. */
    private void renderVisitor() {
        quickLinks.getChildren().setAll(
                quickButton("Book a visit",    "reserve", true),
                quickButton("My reservations", "myres",   false));
        statsRow.getChildren().setAll(loadingCard("NEXT VISIT"), loadingCard("ACTIVE RESERVATIONS"));
        hideAlert();

        // Resolve park names first (best-effort) so the next-visit card can name
        // the park, then load the reservations themselves.
        network.listParks().thenAccept(res -> loadVisitorReservations(parkNamesFrom(res)));

        network.listNotifications().thenAccept(res -> {
            visitorUnread = unreadCount(res);
            refreshVisitorAlert();
        });
    }

    /**
     * Loads the visitor's reservations and fills the next-visit/active cards.
     *
     * @param parkNames park id &rarr; name lookup for naming the next visit's park
     */
    private void loadVisitorReservations(Map<Integer, String> parkNames) {
        network.listReservations(session.getActorId()).thenAccept(res -> {
            List<ReservationDTO> rows = reservationsFrom(res);

            LocalDate today = LocalDate.now();
            ReservationDTO next = null;
            LocalDate nextDate = null;
            int active = 0, confirmed = 0;
            visitorPending = 0;
            visitorWaiting = 0;

            // Plain loop (no streams): tally the live reservations and find the
            // soonest upcoming PENDING/CONFIRMED visit, ignoring past dates and
            // cancelled/completed/no-show rows.
            for (ReservationDTO r : rows) {
                switch (r.getStatus()) {
                    case PENDING   -> { active++; visitorPending++; }
                    case CONFIRMED -> { active++; confirmed++; }
                    case WAITING   -> { active++; visitorWaiting++; }
                    default        -> { /* CANCELLED / COMPLETED / NO_SHOW: not active */ }
                }
                if (r.getStatus() == ReservationStatus.PENDING
                        || r.getStatus() == ReservationStatus.CONFIRMED) {
                    LocalDate d = parseDate(r.getVisitDate());
                    if (d != null && !d.isBefore(today) && (nextDate == null || d.isBefore(nextDate))) {
                        next = r;
                        nextDate = d;
                    }
                }
            }

            statsRow.getChildren().setAll(
                    nextVisitCard(next, nextDate, parkNames),
                    activeCard(active, confirmed, visitorPending, visitorWaiting));
            refreshVisitorAlert();
        });
    }

    /**
     * Builds the "next visit" card, or an empty-state card when there is none.
     *
     * @param next      the soonest upcoming reservation, or {@code null}
     * @param nextDate  that reservation's date, or {@code null}
     * @param parkNames park id &rarr; name lookup
     * @return the next-visit stat card
     */
    private VBox nextVisitCard(ReservationDTO next, LocalDate nextDate, Map<Integer, String> parkNames) {
        if (next == null) {
            return statCard("NEXT VISIT", "No upcoming visits", "Book a visit to get started");
        }
        String park = parkNames.getOrDefault(next.getParkId(), "Park #" + next.getParkId());
        return statCard("NEXT VISIT", formatDate(nextDate), park);
    }

    /**
     * Builds the "active reservations" card with a per-status breakdown.
     *
     * @param active    total active reservations
     * @param confirmed confirmed count
     * @param pending   pending count
     * @param waiting   waiting-list count
     * @return the active-reservations stat card
     */
    private VBox activeCard(int active, int confirmed, int pending, int waiting) {
        String sub;
        if (active == 0) {
            sub = "nothing booked yet";
        } else {
            StringBuilder b = new StringBuilder();
            b.append(confirmed).append(" confirmed · ").append(pending).append(" pending");
            if (waiting > 0) b.append(" · ").append(waiting).append(" waiting");
            sub = b.toString();
        }
        return statCard("ACTIVE RESERVATIONS", String.valueOf(active), sub);
    }

    /** Recomputes the visitor attention line from the pending/waiting/unread counts. */
    private void refreshVisitorAlert() {
        List<String> items = new ArrayList<>();
        if (visitorPending > 0) items.add(plural(visitorPending, "reservation") + " to confirm");
        if (visitorWaiting > 0) items.add(plural(visitorWaiting, "waiting-list request") + " active");
        if (visitorUnread  > 0) items.add(plural(visitorUnread,  "unread notification"));

        if (items.isEmpty()) {
            showAlert("You're all caught up — nothing needs your attention.", true);
        } else {
            showAlert("Needs attention:   " + String.join("    ·    ", items), false);
        }
    }

    /* ---------- PARK_EMPLOYEE ---------------------------------------------- */

    /** Renders the park-employee dashboard: a live occupancy card for their park. */
    private void renderParkEmployee() {
        quickLinks.getChildren().setAll(quickButton("Open gate", "gate", true));
        statsRow.getChildren().setAll(loadingCard("CURRENT OCCUPANCY"));
        hideAlert();

        // Resolve park names, then the employee's own live occupancy (the server
        // derives the park from the session — no id is sent).
        network.listParks().thenAccept(parksRes -> {
            Map<Integer, String> names = parkNamesFrom(parksRes);
            network.currentOccupancy().thenAccept(res -> {
                if (res.isSuccess() && res.getData() instanceof OccupancyDTO occ) {
                    int ceiling = occ.getMaxCapacity() - occ.getGapSize();
                    String park = names.getOrDefault(occ.getParkId(), "Park #" + occ.getParkId());
                    statsRow.getChildren().setAll(statCard(
                            "CURRENT OCCUPANCY",
                            occ.getCurrent() + " / " + ceiling,
                            park + "   ·   " + occ.getAvailable() + " free"));
                    if (occ.getAvailable() <= 0) {
                        showAlert("Park is at capacity — no free spaces right now.", false);
                    }
                } else {
                    statsRow.getChildren().setAll(errorCard("CURRENT OCCUPANCY", res.getMessage()));
                }
            });
        });
    }

    /* ---------- DEPT_MANAGER ----------------------------------------------- */

    /** Renders the department-manager dashboard: a pending-approvals card. */
    private void renderDeptManager() {
        quickLinks.getChildren().setAll(
                quickButton("Approval queue", "approvals",    true),
                quickButton("Reports",        "visitsreport", false));
        statsRow.getChildren().setAll(loadingCard("PENDING APPROVALS"));
        hideAlert();

        network.listPendingChanges().thenAccept(res -> {
            if (res.isSuccess() && res.getData() instanceof List<?> raw) {
                int n = raw.size();
                statsRow.getChildren().setAll(statCard(
                        "PENDING APPROVALS",
                        String.valueOf(n),
                        n == 1 ? "request awaits approval" : "requests await approval"));
                if (n > 0) {
                    showAlert(plural(n, "parameter-change request") + " awaiting your approval.", false);
                } else {
                    showAlert("No parameter changes are awaiting approval.", true);
                }
            } else {
                statsRow.getChildren().setAll(errorCard("PENDING APPROVALS", res.getMessage()));
            }
        });
    }

    /* ---------- PARK_MANAGER ----------------------------------------------- */

    /** Renders the park-manager dashboard: their park's configured capacity card. */
    private void renderParkManager() {
        quickLinks.getChildren().setAll(quickButton("Park parameters", "parkparams", true));
        statsRow.getChildren().setAll(loadingCard("PARK CAPACITY"));
        hideAlert();

        // Live occupancy is gated to PARK_EMPLOYEE server-side, so a manager reads
        // their own park's configured capacity from LIST_PARKS instead (a plain
        // loop picks out their park by the session's park id).
        Integer myPark = session.getParkId();
        network.listParks().thenAccept(res -> {
            ParkDTO mine = null;
            if (res.isSuccess() && res.getData() instanceof List<?> raw && myPark != null) {
                for (Object o : raw) {
                    if (o instanceof ParkDTO p && p.getId() == myPark) {
                        mine = p;
                        break;
                    }
                }
            }
            if (mine != null) {
                int bookable = mine.getMaxCapacity() - mine.getGapSize();
                statsRow.getChildren().setAll(statCard(
                        "PARK CAPACITY",
                        String.valueOf(mine.getMaxCapacity()),
                        mine.getName() + "   ·   gap " + mine.getGapSize() + "   ·   bookable " + bookable));
            } else {
                statsRow.getChildren().setAll(errorCard("PARK CAPACITY",
                        res.isSuccess() ? "Your park could not be found." : res.getMessage()));
            }
        });
    }

    /* ---------- SERVICE_REP ------------------------------------------------ */

    /** Renders the service-rep dashboard: registration quick links, no metric card. */
    private void renderServiceRep() {
        quickLinks.getChildren().setAll(
                quickButton("Register subscriber", "regsub",   true),
                quickButton("Register guide",      "regguide", false));
        // No metric for a service rep — collapse the (empty) cards row and alert.
        statsRow.setVisible(false);
        statsRow.setManaged(false);
        hideAlert();
    }

    /* ---------- Card / button / alert builders ----------------------------- */

    /**
     * Builds a stat card with a label, a big value, and a sub-line.
     *
     * @param label the card's caption
     * @param value the headline value
     * @param sub   the sub-line detail
     * @return the assembled stat card
     */
    private VBox statCard(String label, String value, String sub) {
        VBox card = new VBox(
                withClass(new Label(label), "stat-card-label"),
                withClass(new Label(value), "stat-card-value"),
                withClass(new Label(sub),   "stat-card-sub"));
        card.getStyleClass().add("stat-card");
        HBox.setHgrow(card, Priority.ALWAYS);
        card.setMaxWidth(Double.MAX_VALUE);
        return card;
    }

    /**
     * Placeholder card shown while a metric is still loading.
     *
     * @param label the card's caption
     * @return a loading stat card
     */
    private VBox loadingCard(String label) {
        return statCard(label, "…", "loading…");
    }

    /**
     * Card shown when a metric request fails, surfacing the server's message.
     *
     * @param label   the card's caption
     * @param message the server's error message (or blank for "unavailable")
     * @return an error stat card
     */
    private VBox errorCard(String label, String message) {
        return statCard(label, "—", (message == null || message.isBlank()) ? "unavailable" : message);
    }

    /**
     * Builds a quick-link button that navigates to a screen on click.
     *
     * @param text    the button label
     * @param navId   the target screen id
     * @param primary whether to use primary (vs secondary) styling
     * @return the quick-link button
     */
    private Button quickButton(String text, String navId, boolean primary) {
        Button b = new Button(text);
        b.getStyleClass().add(primary ? "btn-primary" : "btn-secondary");
        b.setMinWidth(Region.USE_PREF_SIZE); // never ellipsize the label in a tight row
        b.setOnAction(e -> navigator.go(navId));
        return b;
    }

    /**
     * Shows the attention banner.
     *
     * @param text the banner text
     * @param calm whether to use the calm (all-caught-up) styling
     */
    private void showAlert(String text, boolean calm) {
        alertLabel.setText(text);
        alertLabel.getStyleClass().remove("calm");
        if (calm) alertLabel.getStyleClass().add("calm");
        alertLabel.setVisible(true);
        alertLabel.setManaged(true);
    }

    /** Hides the attention banner. */
    private void hideAlert() {
        alertLabel.setVisible(false);
        alertLabel.setManaged(false);
    }

    /* ---------- Response → value extraction (defensive, never throws) ------- */

    /**
     * Builds the park id &rarr; name lookup from a LIST_PARKS response.
     *
     * @param res the LIST_PARKS response
     * @return a park id &rarr; name map (empty on failure)
     */
    private Map<Integer, String> parkNamesFrom(ServerResponse res) {
        Map<Integer, String> names = new HashMap<>();
        if (res.isSuccess() && res.getData() instanceof List<?> raw) {
            for (Object o : raw) {
                if (o instanceof ParkDTO p) names.put(p.getId(), p.getName());
            }
        }
        return names;
    }

    /**
     * Extracts the reservation list from a LIST_RESERVATIONS response.
     *
     * @param res the response
     * @return the reservations (empty on failure)
     */
    private List<ReservationDTO> reservationsFrom(ServerResponse res) {
        List<ReservationDTO> rows = new ArrayList<>();
        if (res.isSuccess() && res.getData() instanceof List<?> raw) {
            for (Object o : raw) {
                if (o instanceof ReservationDTO r) rows.add(r);
            }
        }
        return rows;
    }

    /**
     * Counts unacknowledged notifications in a LIST_NOTIFICATIONS response.
     *
     * @param res the response
     * @return the number of unread notifications (0 on failure)
     */
    private int unreadCount(ServerResponse res) {
        int n = 0;
        if (res.isSuccess() && res.getData() instanceof List<?> raw) {
            for (Object o : raw) {
                if (o instanceof NotificationDTO note && !note.isAcknowledged()) n++;
            }
        }
        return n;
    }

    /* ---------- Small formatting helpers ----------------------------------- */

    /** Date format for the next-visit card (e.g. {@code "Jun 21, 2026"}). */
    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.ENGLISH);

    /**
     * Parses an ISO date, returning {@code null} on null/invalid input.
     *
     * @param iso the ISO {@code yyyy-MM-dd} string, or {@code null}
     * @return the parsed date, or {@code null}
     */
    private static LocalDate parseDate(String iso) {
        if (iso == null) return null;
        try {
            return LocalDate.parse(iso);
        } catch (Exception ex) {
            return null;
        }
    }

    /**
     * Formats a date with {@link #DATE_FMT}, or an em dash for {@code null}.
     *
     * @param d the date, or {@code null}
     * @return the formatted date, or an em dash
     */
    private static String formatDate(LocalDate d) {
        return d == null ? "—" : d.format(DATE_FMT);
    }

    /**
     * Extracts the first name from a display name, defaulting to "there".
     *
     * @param name the full display name, or {@code null}
     * @return the first word, or "there" if blank
     */
    private static String firstNameOf(String name) {
        if (name == null || name.isBlank()) return "there";
        return name.trim().split("\\s+")[0];
    }

    /**
     * Title-cases a role label: "PARK_EMPLOYEE" → "Park Employee"; "Subscriber"
     * stays "Subscriber".
     *
     * @param roleLabel the raw role label
     * @return the human-friendly role label
     */
    private static String prettyRole(String roleLabel) {
        if (roleLabel == null || roleLabel.isBlank()) return "";
        StringBuilder b = new StringBuilder();
        for (String w : roleLabel.toLowerCase(Locale.ENGLISH).split("[_\\s]+")) {
            if (w.isEmpty()) continue;
            if (b.length() > 0) b.append(' ');
            b.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1));
        }
        return b.toString();
    }

    /**
     * Formats a count with a pluralized noun (1 item / 2 items).
     *
     * @param n    the count
     * @param noun the singular noun
     * @return {@code "<n> <noun>[s]"}
     */
    private static String plural(int n, String noun) {
        return n + " " + noun + (n == 1 ? "" : "s");
    }

    /**
     * Adds a style class to a node and returns it (for inline use).
     *
     * @param node the node to style
     * @param cls  the style class to add
     * @param <T>  the node type
     * @return the same node, for chaining
     */
    private static <T extends javafx.scene.Node> T withClass(T node, String cls) {
        node.getStyleClass().add(cls);
        return node;
    }
}
