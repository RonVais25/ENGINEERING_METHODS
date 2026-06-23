package client.view;

import client.service.NetworkService;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;

import java.util.function.LongConsumer;

/**
 * Controller for RegisterVisitorView.fxml — the self-service signup dialog opened
 * from the login screen's "Create an account" button.
 *
 * <p>Collects a national ID, full name, email, phone and a password (entered
 * twice), validates them client-side for quick feedback, then sends
 * {@code REGISTER_VISITOR} via {@link NetworkService}. The server is the
 * authority: it re-checks the inputs, stores the password and rejects a national
 * ID that is already registered. A self-registered account is a regular visitor
 * (NOT a subscriber); the service rep still owns the subscriber path.
 *
 * <p>On success it briefly shows a confirmation, then returns to the login screen
 * with the new national ID prefilled (via {@code onRegistered}) and closes itself
 * (via {@code onClose}) so the visitor can sign in immediately. Both collaborators
 * are supplied by {@link UserLoginController} when it constructs this controller.
 */
public class RegisterVisitorController {

    /** Visitor national-id input. */
    @FXML private TextField     idField;
    /** Visitor full-name input. */
    @FXML private TextField     nameField;
    /** Visitor email input. */
    @FXML private TextField     emailField;
    /** Visitor phone input. */
    @FXML private TextField     phoneField;
    /** Chosen-password input. */
    @FXML private PasswordField passwordField;
    /** Confirm-password input. */
    @FXML private PasswordField confirmField;
    /** Submits the registration. */
    @FXML private Button        registerBtn;
    /** Result/toast label for signup feedback. */
    @FXML private Label         resultLabel;

    /** Shared network service for the signup call. */
    private final NetworkService network;
    /** Invoked with the new national id on success, to prefill the login form. */
    private final LongConsumer   onRegistered;
    /** Closes the dialog. */
    private final Runnable       onClose;

    /**
     * Creates the registration controller.
     *
     * @param network      the shared network service
     * @param onRegistered callback given the new national id on a successful signup
     * @param onClose      callback that closes the dialog
     */
    public RegisterVisitorController(NetworkService network, LongConsumer onRegistered, Runnable onClose) {
        this.network      = network;
        this.onRegistered = onRegistered;
        this.onClose      = onClose;
    }

    /** Register-button handler: validates the form and sends REGISTER_VISITOR. */
    @FXML
    private void onRegister() {
        String idRaw = idField.getText() == null ? "" : idField.getText().trim();
        long visitorId;
        try {
            visitorId = Long.parseLong(idRaw);
        } catch (NumberFormatException ex) {
            Widgets.showToast(resultLabel, false, "Enter a valid numeric National ID");
            return;
        }

        String fullName = nameField.getText()  == null ? "" : nameField.getText().trim();
        String email    = emailField.getText() == null ? "" : emailField.getText().trim();
        String phone    = phoneField.getText() == null ? "" : phoneField.getText().trim();
        String password = passwordField.getText() == null ? "" : passwordField.getText();
        String confirm  = confirmField.getText()  == null ? "" : confirmField.getText();

        if (fullName.isEmpty() || email.isEmpty() || phone.isEmpty() || password.isEmpty()) {
            Widgets.showToast(resultLabel, false, "All fields are required");
            return;
        }
        // Basic shape check only — the server remains the authority on the account.
        if (!email.matches("[^@\\s]+@[^@\\s]+\\.[^@\\s]+")) {
            Widgets.showToast(resultLabel, false, "Enter a valid email address");
            return;
        }
        if (!password.equals(confirm)) {
            Widgets.showToast(resultLabel, false, "Passwords do not match");
            return;
        }

        registerBtn.setText("Creating…");
        registerBtn.setDisable(true);
        network.registerVisitor(visitorId, fullName, email, phone, password).thenAccept(res -> {
            if (res.isSuccess()) {
                Widgets.showToast(resultLabel, true, res.getMessage());
                // Brief beat so the confirmation is visible, then hand the new id
                // back to the login screen and close.
                Thread t = new Thread(() -> {
                    try { Thread.sleep(1200); } catch (InterruptedException ignored) {}
                    Platform.runLater(() -> {
                        onRegistered.accept(visitorId);
                        onClose.run();
                    });
                });
                t.setDaemon(true);
                t.start();
            } else {
                Widgets.showToast(resultLabel, false, res.getMessage());
                registerBtn.setText("Create Account");
                registerBtn.setDisable(false);
            }
        });
    }

    /** Cancel-button handler: closes the dialog without registering. */
    @FXML
    private void onCancel() {
        onClose.run();
    }
}
