package client.view;

import client.app.Session;
import client.service.NetworkService;
import common.dto.ParkDTO;
import common.dto.ServerResponse;
import common.dto.UserDTO;
import common.dto.VisitorDTO;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.layout.GridPane;

import java.util.List;
import java.util.Locale;

/**
 * "My Profile": a read-only screen showing the logged-in actor's personal details,
 * read straight from the live {@link Session} — no server round-trip for the data
 * itself. A visitor sees their name, national ID, email, phone and subscriber
 * status; a staff user sees their name, username, role and park.
 *
 * <p>The only network call is a best-effort {@code LIST_PARKS} to turn a staff
 * user's park id into the park's name (the same lookup the Dashboard already uses);
 * it is not a new server op, and on failure the park falls back to {@code "Park #<id>"}.
 *
 * <p>Plain controller (no push subscriptions), so it does not extend
 * {@link BaseController} — mirroring {@link DashboardController}.
 */
public class MyProfileController {

    /** Avatar initials of the logged-in actor. */
    @FXML private Label    initialsLbl;
    /** Display name of the logged-in actor. */
    @FXML private Label    nameLbl;
    /** Short identity sub-line (role / subscriber status). */
    @FXML private Label    subLbl;
    /** Grid the personal key/value rows are rendered into. */
    @FXML private GridPane detailGrid;

    /** The current client session (the sole source of the displayed data). */
    private final Session        session;
    /** Shared network service — used only for the best-effort park-name lookup. */
    private final NetworkService network;

    /**
     * Creates the profile controller.
     *
     * @param network the shared network service (park-name lookup only)
     * @param session the current client session
     */
    public MyProfileController(NetworkService network, Session session) {
        this.network = network;
        this.session = session;
    }

    /** FXML lifecycle hook: fills the header and the role-appropriate detail rows. */
    @FXML
    private void initialize() {
        String name = session.getDisplayName();
        initialsLbl.setText(initialsOf(name));
        nameLbl.setText(orDash(name));
        subLbl.setText(prettyRole(session.getRoleLabel()));

        if (session.isVisitor()) {
            renderVisitor(session.getVisitor());
        } else if (session.isStaff()) {
            renderStaff(session.getUser());
        }
    }

    /**
     * Renders a visitor's personal details: name, national ID, email, phone and
     * subscriber status — all straight from the {@link VisitorDTO} in the session.
     *
     * @param v the logged-in visitor
     */
    private void renderVisitor(VisitorDTO v) {
        int row = 0;
        addRow(row++, "Name",              orDash(v.getFullName()));
        addRow(row++, "National ID",       String.valueOf(v.getId()));
        addRow(row++, "Email",             orDash(v.getEmail()));
        addRow(row++, "Phone",             orDash(v.getPhone()));
        addRow(row++, "Subscriber status", v.isSubscriber() ? "Subscriber" : "Not a subscriber");
    }

    /**
     * Renders a staff user's details: name, username, role and park. The park id is
     * resolved to its name via a best-effort {@code LIST_PARKS} (falling back to
     * {@code "Park #<id>"}, or {@code "—"} when the user has no park).
     *
     * @param u the logged-in staff user
     */
    private void renderStaff(UserDTO u) {
        int row = 0;
        addRow(row++, "Name",     orDash(u.getFullName()));
        addRow(row++, "Username", orDash(u.getUsername()));
        addRow(row++, "Role",     prettyRole(u.getRole() == null ? "" : u.getRole().name()));
        Label parkVal = addRow(row++, "Park", u.getParkId() == null ? "—" : "Park #" + u.getParkId());

        // Best-effort: replace the bare "Park #<id>" with the park's name once
        // LIST_PARKS resolves (open to any logged-in actor; not a new server op).
        // The future completes on the FX thread, so updating the label is safe.
        Integer parkId = u.getParkId();
        if (parkId != null) {
            network.listParks().thenAccept(res -> {
                String name = parkNameFrom(res, parkId);
                if (name != null) parkVal.setText(name);
            });
        }
    }

    /**
     * Finds a park's name in a LIST_PARKS response (defensive, never throws).
     *
     * @param res    the LIST_PARKS response
     * @param parkId the park id to resolve
     * @return the park's name, or {@code null} if the response had no such park
     */
    private String parkNameFrom(ServerResponse res, int parkId) {
        if (res.isSuccess() && res.getData() instanceof List<?> raw) {
            for (Object o : raw) {
                if (o instanceof ParkDTO p && p.getId() == parkId) return p.getName();
            }
        }
        return null;
    }

    /**
     * Adds one read-only "key: value" row to the detail grid and returns the value
     * label so a caller can update it later (e.g. the async park name).
     *
     * @param row   the grid row index
     * @param key   the field label
     * @param value the field value
     * @return the value label
     */
    private Label addRow(int row, String key, String value) {
        Label k = new Label(key);
        k.getStyleClass().add("key");
        Label v = new Label(value);
        v.getStyleClass().add("val");
        detailGrid.add(k, 0, row);
        detailGrid.add(v, 1, row);
        return v;
    }

    /**
     * Renders a blank/null value as an em dash so empty fields read cleanly.
     *
     * @param value the value to display
     * @return the value, or an em dash if it is null/blank
     */
    private static String orDash(String value) {
        return (value == null || value.isBlank()) ? "—" : value;
    }

    /**
     * First letters of the first two name tokens, e.g. "Dana Department" → "DD".
     *
     * @param name the display name
     * @return up to two uppercase initials, or "?" if blank
     */
    private static String initialsOf(String name) {
        if (name == null || name.isBlank()) return "?";
        String[] parts = name.trim().split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.length && sb.length() < 2; i++) {
            if (!parts[i].isEmpty()) sb.append(Character.toUpperCase(parts[i].charAt(0)));
        }
        return sb.length() == 0 ? "?" : sb.toString();
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
}
