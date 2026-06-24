package client.view;

import client.app.Session;
import client.service.NetworkService;
import common.dto.ParkDTO;
import common.dto.ServerResponse;
import common.dto.UserDTO;
import common.dto.VisitorDTO;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * "My Profile": shows the logged-in actor's personal details, read straight from
 * the live {@link Session}, and lets the actor edit their OWN editable fields. A
 * visitor sees (and can edit) name, email and phone — national ID and subscriber
 * status stay read-only; a staff user sees name, worker id, username, email, role
 * and park, and can edit only their name (worker id and email are display-only).
 *
 * <p>Editing is an in-place toggle: each editable row holds both a read-only value
 * label and a text field in the same grid cell, and {@code Edit} flips which one
 * shows (swapping the {@code Edit} button for {@code Save}/{@code Cancel}). Save
 * validates client-side (name non-empty, email shape), then sends
 * {@code UPDATE_PROFILE}; the server is the authority and edits only the logged-in
 * actor's row (no id is sent). On success the refreshed DTO is written back into
 * the {@link Session} so other screens (e.g. booking prefill) see the new contact.
 *
 * <p>The only other network call is a best-effort {@code LIST_PARKS} to turn a
 * staff user's park id into the park's name (the same lookup the Dashboard uses);
 * on failure the park falls back to {@code "Park #<id>"}.
 *
 * <p>Plain controller (no push subscriptions), so it does not extend
 * {@link BaseController} — mirroring {@link DashboardController}.
 */
public class MyProfileController {

    /** Avatar initials of the logged-in actor. */
    @FXML private Label     initialsLbl;
    /** Display name of the logged-in actor. */
    @FXML private Label     nameLbl;
    /** Short identity sub-line (role / subscriber status). */
    @FXML private Label     subLbl;
    /** Grid the personal key/value rows are rendered into. */
    @FXML private GridPane  detailGrid;
    /** Starts an edit (shown when not editing). */
    @FXML private Button    editBtn;
    /** Commits an edit (shown while editing). */
    @FXML private Button    saveBtn;
    /** Abandons an edit (shown while editing). */
    @FXML private Button    cancelBtn;
    /** Inline success/error feedback for a save. */
    @FXML private Label     resultLabel;

    /** The current client session (the source of the displayed data). */
    private final Session        session;
    /** Shared network service — the park-name lookup and the profile save. */
    private final NetworkService network;

    /** Every editable row, so a single toggle can flip them all between view/edit. */
    private final List<EditableField> editableFields = new ArrayList<>();

    /** The name row (visitor and staff both have one). */
    private EditableField nameField;
    /** The email row (visitor only; {@code null} for staff). */
    private EditableField emailField;
    /** The phone row (visitor only; {@code null} for staff). */
    private EditableField phoneField;

    /**
     * Basic email shape check — the same shape the self-service signup uses. The
     * server re-validates, so this is only quick client-side feedback.
     */
    private static final String EMAIL_PATTERN = "[^@\\s]+@[^@\\s]+\\.[^@\\s]+";

    /**
     * Creates the profile controller.
     *
     * @param network the shared network service (park-name lookup + profile save)
     * @param session the current client session
     */
    public MyProfileController(NetworkService network, Session session) {
        this.network = network;
        this.session = session;
    }

    /** FXML lifecycle hook: fills the header and the role-appropriate detail rows. */
    @FXML
    private void initialize() {
        refreshHeader(session.getDisplayName());
        subLbl.setText(prettyRole(session.getRoleLabel()));

        if (session.isVisitor()) {
            renderVisitor(session.getVisitor());
        } else if (session.isStaff()) {
            renderStaff(session.getUser());
        }

        setEditing(false);
    }

    /**
     * Renders a visitor's personal details. Name, email and phone are editable
     * rows; national ID and subscriber status stay read-only.
     *
     * @param v the logged-in visitor
     */
    private void renderVisitor(VisitorDTO v) {
        int row = 0;
        nameField  = addEditableRow(row++, "Name",  v.getFullName());
        addRow(row++, "National ID",       String.valueOf(v.getId()));
        emailField = addEditableRow(row++, "Email", v.getEmail());
        phoneField = addEditableRow(row++, "Phone", v.getPhone());
        addRow(row++, "Subscriber status", v.isSubscriber() ? "Subscriber" : "Not a subscriber");
    }

    /**
     * Renders a staff user's details. Only the name is editable; worker id (the
     * user id), username, email, role and park stay read-only. The park id is
     * resolved to its name via a best-effort {@code LIST_PARKS} (falling back to
     * {@code "Park #<id>"}, or {@code "—"} when the user has no park).
     *
     * @param u the logged-in staff user
     */
    private void renderStaff(UserDTO u) {
        int row = 0;
        nameField = addEditableRow(row++, "Name", u.getFullName());
        addRow(row++, "Worker ID", String.valueOf(u.getId()));
        addRow(row++, "Username",  orDash(u.getUsername()));
        addRow(row++, "Email",     orDash(u.getEmail()));
        addRow(row++, "Role",      prettyRole(u.getRole() == null ? "" : u.getRole().name()));
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

    /* ---------- Editing --------------------------------------------------- */

    /** Edit-button handler: enters edit mode with the fields holding current values. */
    @FXML
    private void onEdit() {
        syncInputsFromSession();
        setEditing(true);
    }

    /** Cancel-button handler: discards any edits and returns to read-only view. */
    @FXML
    private void onCancel() {
        syncInputsFromSession();
        setEditing(false);
    }

    /**
     * Save-button handler: validates the editable fields client-side (name
     * non-empty, and for a visitor a basic email shape), then sends
     * {@code UPDATE_PROFILE}. On success the refreshed DTO updates the session and
     * the displayed values; on failure the inline toast shows why.
     */
    @FXML
    private void onSave() {
        String name = nameField.value();
        if (name.isEmpty()) {
            Widgets.showToast(resultLabel, false, "Name cannot be empty");
            return;
        }

        String email = null;
        String phone = null;
        if (session.isVisitor()) {
            email = emailField.value();
            phone = phoneField.value();
            if (!email.matches(EMAIL_PATTERN)) {
                Widgets.showToast(resultLabel, false, "Enter a valid email address");
                return;
            }
        }

        saveBtn.setDisable(true);
        cancelBtn.setDisable(true);
        network.updateProfile(name, email, phone).thenAccept(res -> {
            saveBtn.setDisable(false);
            cancelBtn.setDisable(false);
            if (res.isSuccess()) {
                applyRefresh(res.getData());
                setEditing(false);
                Widgets.showToast(resultLabel, true, "Profile updated");
            } else {
                Widgets.showToast(resultLabel, false, res.getMessage());
            }
        });
    }

    /**
     * Toggles the screen between read-only view and edit mode: flips every editable
     * row's label/text-field, and swaps the {@code Edit} button for {@code Save}/{@code Cancel}.
     *
     * @param editing {@code true} to show the text fields and Save/Cancel
     */
    private void setEditing(boolean editing) {
        for (EditableField f : editableFields) f.setEditing(editing);
        editBtn.setVisible(!editing);   editBtn.setManaged(!editing);
        saveBtn.setVisible(editing);    saveBtn.setManaged(editing);
        cancelBtn.setVisible(editing);  cancelBtn.setManaged(editing);
    }

    /** Resets the edit text fields to the current session values (raw, no em dash). */
    private void syncInputsFromSession() {
        if (session.isVisitor()) {
            VisitorDTO v = session.getVisitor();
            nameField.setText(v.getFullName());
            emailField.setText(v.getEmail());
            phoneField.setText(v.getPhone());
        } else if (session.isStaff()) {
            nameField.setText(session.getUser().getFullName());
        }
    }

    /**
     * Applies the server's refreshed DTO: updates the in-memory session (so other
     * screens see the new contact), the header, the read-only value labels and the
     * edit fields.
     *
     * @param data the refreshed {@link VisitorDTO} / {@link UserDTO} from the server
     */
    private void applyRefresh(Object data) {
        if (data instanceof VisitorDTO v) {
            session.setVisitor(v);
            nameField.update(v.getFullName());
            emailField.update(v.getEmail());
            phoneField.update(v.getPhone());
            refreshHeader(v.getFullName());
        } else if (data instanceof UserDTO u) {
            session.setUser(u);
            nameField.update(u.getFullName());
            refreshHeader(u.getFullName());
        }
    }

    /**
     * Updates the avatar initials and the header name.
     *
     * @param name the display name to show
     */
    private void refreshHeader(String name) {
        initialsLbl.setText(initialsOf(name));
        nameLbl.setText(orDash(name));
    }

    /* ---------- Grid rows ------------------------------------------------- */

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
     * Adds one editable "key: value" row: a read-only value label plus a hidden
     * text field sharing the same value cell. {@link #setEditing} toggles which is
     * shown.
     *
     * @param row   the grid row index
     * @param key   the field label
     * @param value the current field value (may be {@code null}/blank)
     * @return the editable field handle, also registered for the edit toggle
     */
    private EditableField addEditableRow(int row, String key, String value) {
        Label k = new Label(key);
        k.getStyleClass().add("key");

        Label display = new Label(orDash(value));
        display.getStyleClass().add("val");

        TextField input = new TextField(value == null ? "" : value);
        input.getStyleClass().add("edit-field");
        input.setVisible(false);
        input.setManaged(false);

        detailGrid.add(k, 0, row);
        detailGrid.add(display, 1, row);
        detailGrid.add(input, 1, row);   // same cell as display; visibility-toggled

        EditableField f = new EditableField(display, input);
        editableFields.add(f);
        return f;
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

    /* ---------- Formatting helpers ---------------------------------------- */

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

    /**
     * One editable detail row: its read-only display label and the text field that
     * replaces it in edit mode. Both live in the same grid cell; only their
     * {@code visible}/{@code managed} flags differ between the two modes.
     */
    private static final class EditableField {
        /** The read-only value label shown in view mode. */
        private final Label     display;
        /** The text field shown (in the same cell) in edit mode. */
        private final TextField input;

        /**
         * Binds a row's display label to its edit field.
         *
         * @param display the read-only value label
         * @param input   the edit text field
         */
        EditableField(Label display, TextField input) {
            this.display = display;
            this.input   = input;
        }

        /** {@return the trimmed text currently in the edit field} */
        String value() {
            return input.getText() == null ? "" : input.getText().trim();
        }

        /**
         * Sets the edit field's text (raw, with {@code null} treated as blank).
         *
         * @param value the value to load into the edit field
         */
        void setText(String value) {
            input.setText(value == null ? "" : value);
        }

        /**
         * Writes a saved value into both the display label and the edit field.
         *
         * @param value the new persisted value
         */
        void update(String value) {
            display.setText(orDash(value));
            setText(value);
        }

        /**
         * Shows the edit field (and hides the label) or vice versa.
         *
         * @param editing {@code true} to show the edit field
         */
        void setEditing(boolean editing) {
            display.setVisible(!editing); display.setManaged(!editing);
            input.setVisible(editing);    input.setManaged(editing);
        }
    }
}
