package client.view;

import client.app.Session;
import client.service.NetworkService;
import common.dto.UserDTO;
import common.dto.VisitorDTO;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.VBox;

import java.util.List;

/**
 * Controller for UserLoginView.fxml — the identity login that runs after the
 * connection screen and again after logout. A Staff / Visitor segmented toggle
 * swaps the visible field set; submit sends {@code LOGIN_STAFF} or
 * {@code LOGIN_VISITOR} via {@link NetworkService}.
 *
 * <p>On success it stores the returned DTO in the {@link Session} and runs the
 * supplied {@code onSuccess} callback (which opens the main shell). On failure
 * it surfaces the server's message verbatim — including "Invalid username or
 * password." and "This user is already logged in elsewhere."
 *
 * <p>For convenience while testing role-gated screens, a dev-only "Quick login"
 * row of one-click buttons fills in and submits a seeded account (see
 * {@link #quickAccounts()}). It assumes the seed password {@code changeme}; the
 * normal username/password fields are untouched and still work as before.
 */
public class UserLoginController {

    /** Shared password of every seeded account in {@code setup.sql}. */
    private static final String DEV_PASSWORD = "changeme";

    @FXML private ToggleButton  staffTab;
    @FXML private ToggleButton  visitorTab;
    @FXML private VBox          staffPane;
    @FXML private VBox          visitorPane;
    @FXML private TextField     usernameField;
    @FXML private PasswordField passwordField;
    @FXML private TextField     visitorIdField;
    @FXML private Button        submitBtn;
    @FXML private Label         errorLabel;
    @FXML private FlowPane      quickLoginBox;

    private final NetworkService network;
    private final Session        session;
    private final Runnable       onSuccess;

    public UserLoginController(NetworkService network, Session session, Runnable onSuccess) {
        this.network   = network;
        this.session   = session;
        this.onSuccess = onSuccess;
    }

    @FXML
    private void initialize() {
        ToggleGroup group = new ToggleGroup();
        staffTab.setToggleGroup(group);
        visitorTab.setToggleGroup(group);
        staffTab.setSelected(true);

        // Keep exactly one tab selected — clicking the active tab must not
        // deselect it (which would leave both panes hidden).
        group.selectedToggleProperty().addListener((obs, old, sel) -> {
            if (sel == null) {
                old.setSelected(true);
            } else {
                showStaff(sel == staffTab);
            }
        });
        showStaff(true);

        usernameField.setOnAction(e -> onSubmit());
        passwordField.setOnAction(e -> onSubmit());
        visitorIdField.setOnAction(e -> onSubmit());

        buildQuickLoginButtons();
    }

    /** One seeded account reachable via a quick-login button: staff carry a
     *  {@code username}; visitors carry a {@code visitorId} (the other is null). */
    private record Quick(String label, String username, Long visitorId) {}

    /** The seeded accounts from {@code setup.sql}, one quick button each. */
    private List<Quick> quickAccounts() {
        return List.of(
                new Quick("Dept Mgr",    "dept_mgr",    null),
                new Quick("Galilee Mgr", "galilee_mgr", null),
                new Quick("Carmel Mgr",  "carmel_mgr",  null),
                new Quick("Service Rep", "service_rep", null),
                new Quick("Park Emp",    "park_emp",    null),
                new Quick("Subscriber",  null, 200000001L),
                new Quick("Visitor",     null, 200000002L));
    }

    private void buildQuickLoginButtons() {
        for (Quick q : quickAccounts()) {
            Button b = new Button(q.label());
            b.getStyleClass().add("quick-btn");
            b.setOnAction(e -> quickLogin(q));
            quickLoginBox.getChildren().add(b);
        }
    }

    /**
     * Fills in and submits a seeded account in one click. Staff accounts use the
     * shared {@link #DEV_PASSWORD}; visitor accounts log in by national id. The
     * fields are populated (and the matching tab selected) so it stays visible
     * which account was used and remains editable for a retry.
     */
    private void quickLogin(Quick q) {
        if (q.username() != null) {
            staffTab.setSelected(true);
            usernameField.setText(q.username());
            passwordField.setText(DEV_PASSWORD);
            submitStaff();
        } else {
            visitorTab.setSelected(true);
            visitorIdField.setText(String.valueOf(q.visitorId()));
            submitVisitor();
        }
    }

    private void showStaff(boolean staff) {
        staffPane.setVisible(staff);
        staffPane.setManaged(staff);
        visitorPane.setVisible(!staff);
        visitorPane.setManaged(!staff);
        hideError();
    }

    @FXML
    private void onSubmit() {
        if (staffTab.isSelected()) {
            submitStaff();
        } else {
            submitVisitor();
        }
    }

    private void submitStaff() {
        String username = usernameField.getText() == null ? "" : usernameField.getText().trim();
        String password = passwordField.getText() == null ? "" : passwordField.getText();
        if (username.isEmpty() || password.isEmpty()) {
            showError("Enter both username and password");
            return;
        }
        setBusy(true);
        network.loginStaff(username, password).thenAccept(res -> {
            setBusy(false);
            if (res.isSuccess() && res.getData() instanceof UserDTO u) {
                session.setUser(u);
                onSuccess.run();
            } else {
                showError(res.getMessage());
            }
        });
    }

    private void submitVisitor() {
        String raw = visitorIdField.getText() == null ? "" : visitorIdField.getText().trim();
        long visitorId;
        try {
            visitorId = Long.parseLong(raw);
        } catch (NumberFormatException ex) {
            showError("Enter a valid numeric National ID");
            return;
        }
        setBusy(true);
        network.loginVisitor(visitorId).thenAccept(res -> {
            setBusy(false);
            if (res.isSuccess() && res.getData() instanceof VisitorDTO v) {
                session.setVisitor(v);
                onSuccess.run();
            } else {
                showError(res.getMessage());
            }
        });
    }

    private void setBusy(boolean busy) {
        submitBtn.setDisable(busy);
        submitBtn.setText(busy ? "Signing in…" : "Sign In");
    }

    private void showError(String msg) {
        errorLabel.setText(msg == null ? "Login failed" : msg);
        errorLabel.setVisible(true);
        errorLabel.setManaged(true);
    }

    private void hideError() {
        errorLabel.setVisible(false);
        errorLabel.setManaged(false);
    }
}
