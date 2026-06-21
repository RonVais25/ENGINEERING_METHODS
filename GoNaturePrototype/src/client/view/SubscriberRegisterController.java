package client.view;

import client.app.Session;
import client.service.NetworkService;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.control.TextField;

/**
 * SERVICE_REP-only form that registers a new subscriber.
 *
 * <p>Collects the visitor's national ID, full name, phone, email and family
 * size, then sends {@code REGISTER_SUBSCRIBER} via {@link NetworkService}. The
 * server find-or-creates the visitor (so an existing visitor is upgraded, not
 * duplicated), marks them a subscriber, and inserts the subscriber row. On
 * success the visitor's {@code is_subscriber} flag feeds the member discount in
 * {@code PricingService} for their future bookings.
 *
 * <p>The screen is only reachable when a SERVICE_REP is logged in (see
 * {@code MainShellController}); the server independently re-checks the role, so
 * hiding the screen is convenience only.
 *
 * <p>Extends {@link BaseController} for navigation lifecycle parity with the
 * other screens; it does not subscribe to push events.
 */
public class SubscriberRegisterController extends BaseController {

    /** Upper bound for the family-size spinner; the DB default is 1. */
    private static final int MAX_FAMILY_SIZE = 20;

    /** Subscriber national-id input. */
    @FXML private TextField        idField;
    /** Subscriber full-name input. */
    @FXML private TextField        nameField;
    /** Subscriber phone input. */
    @FXML private TextField        phoneField;
    /** Subscriber email input. */
    @FXML private TextField        emailField;
    /** Family-size spinner. */
    @FXML private Spinner<Integer> familySpinner;
    /** Submits the subscriber registration. */
    @FXML private Button           registerBtn;
    /** Result/toast label for registration feedback. */
    @FXML private Label            resultLabel;

    /**
     * Creates the subscriber-registration controller.
     *
     * @param network the shared network service
     * @param session the current client session
     */
    public SubscriberRegisterController(NetworkService network, Session session) {
        super(network);
    }

    /** FXML lifecycle hook: configures the family-size spinner. */
    @FXML
    private void initialize() {
        familySpinner.setValueFactory(
                new SpinnerValueFactory.IntegerSpinnerValueFactory(1, MAX_FAMILY_SIZE, 1));
    }

    /** Register-button handler: validates the form and sends REGISTER_SUBSCRIBER. */
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

        String fullName = nameField.getText() == null ? "" : nameField.getText().trim();
        if (fullName.isEmpty()) {
            Widgets.showToast(resultLabel, false, "Please enter the subscriber's full name");
            return;
        }

        String phone = phoneField.getText() == null ? "" : phoneField.getText().trim();
        String email = emailField.getText() == null ? "" : emailField.getText().trim();

        // Commit any text typed into the editable spinner before reading it.
        try {
            int typed = Integer.parseInt(familySpinner.getEditor().getText().trim());
            if (typed >= 1 && typed <= MAX_FAMILY_SIZE) familySpinner.getValueFactory().setValue(typed);
        } catch (NumberFormatException ignored) {}
        int familySize = familySpinner.getValue();

        registerBtn.setText("Registering…");
        registerBtn.setDisable(true);

        network.registerSubscriber(visitorId, fullName, phone, email, familySize)
               .thenAccept(res -> {
                    registerBtn.setText("★  Register Subscriber");
                    registerBtn.setDisable(false);
                    Widgets.showToast(resultLabel, res.isSuccess(), res.getMessage());
                    if (res.isSuccess()) clearForm();
               });
    }

    /** Resets the form after a successful registration so the rep can add another. */
    private void clearForm() {
        idField.clear();
        nameField.clear();
        phoneField.clear();
        emailField.clear();
        familySpinner.getValueFactory().setValue(1);
    }
}
