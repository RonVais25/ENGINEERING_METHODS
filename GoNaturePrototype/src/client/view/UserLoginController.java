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
import javafx.scene.layout.VBox;

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
 */
public class UserLoginController {

    @FXML private ToggleButton  staffTab;
    @FXML private ToggleButton  visitorTab;
    @FXML private VBox          staffPane;
    @FXML private VBox          visitorPane;
    @FXML private TextField     usernameField;
    @FXML private PasswordField passwordField;
    @FXML private TextField     visitorIdField;
    @FXML private Button        submitBtn;
    @FXML private Label         errorLabel;

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
