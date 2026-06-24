package client.view;

import client.app.Session;
import client.service.NetworkService;
import common.dto.ParkDTO;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;

import java.util.List;

/**
 * SERVICE_REP-only form that registers a visitor as a group guide.
 *
 * <p>Collects the visitor's national ID, full name, phone and email, then sends
 * {@code REGISTER_GUIDE} via {@link NetworkService}. The server find-or-creates
 * the visitor (so an existing visitor is reused, not duplicated) without
 * touching their subscriber status, and inserts the guide row stamped with the
 * logged-in rep's id and today's approval date. Guides are registered directly
 * here — there is no separate approval workflow.
 *
 * <p>The screen is only reachable when a SERVICE_REP is logged in (see
 * {@code MainShellController}); the server independently re-checks the role, so
 * hiding the screen is convenience only.
 *
 * <p>Extends {@link BaseController} for navigation lifecycle parity with the
 * other screens; it does not subscribe to push events.
 */
public class GuideRegisterController extends BaseController {

    /**
     * Home-park dropdown entry: carries the id but renders the name (mirrors the
     * booking form's ParkOption).
     *
     * @param id   the park id sent with the registration
     * @param name the park name shown in the dropdown
     */
    private record ParkOption(int id, String name) {
        @Override public String toString() { return name; }
    }

    /** Guide national-id input. */
    @FXML private TextField idField;
    /** Guide full-name input. */
    @FXML private TextField nameField;
    /** Guide phone input. */
    @FXML private TextField phoneField;
    /** Guide email input. */
    @FXML private TextField emailField;
    /** Home-park selection dropdown (informational home park stored on the guide row). */
    @FXML private ComboBox<ParkOption> parkCombo;
    /** Submits the guide registration. */
    @FXML private Button    registerBtn;
    /** Result/toast label for registration feedback. */
    @FXML private Label     resultLabel;

    /**
     * Creates the guide-registration controller.
     *
     * @param network the shared network service
     * @param session the current client session
     */
    public GuideRegisterController(NetworkService network, Session session) {
        super(network);
    }

    /** FXML lifecycle hook: populates the home-park dropdown from the server. */
    @FXML
    private void initialize() {
        loadParks();
    }

    /**
     * Populates the home-park dropdown from the server via {@code LIST_PARKS}. Each
     * {@link ParkOption} carries the park id (sent with REGISTER_GUIDE), rendering
     * only the name. On failure the dropdown stays empty and a toast explains why.
     */
    private void loadParks() {
        network.listParks().thenAccept(res -> {
            if (!res.isSuccess()) {
                Widgets.showToast(resultLabel, false, res.getMessage());
                return;
            }
            parkCombo.getItems().clear();
            if (res.getData() instanceof List<?> raw) {
                for (Object o : raw) {
                    ParkDTO p = (ParkDTO) o;
                    parkCombo.getItems().add(new ParkOption(p.getId(), p.getName()));
                }
            }
            parkCombo.getSelectionModel().selectFirst();
        });
    }

    /** Register-button handler: validates the form and sends REGISTER_GUIDE. */
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
            Widgets.showToast(resultLabel, false, "Please enter the guide's full name");
            return;
        }

        String phone = phoneField.getText() == null ? "" : phoneField.getText().trim();
        String email = emailField.getText() == null ? "" : emailField.getText().trim();

        // The guide's home park is required: the rep must assign one. The server
        // re-checks (it rejects a missing park), so this is the inline guard.
        ParkOption park = parkCombo.getValue();
        if (park == null) {
            Widgets.showToast(resultLabel, false, "Please choose the guide's home park");
            return;
        }

        registerBtn.setText("Registering…");
        registerBtn.setDisable(true);

        network.registerGuide(visitorId, fullName, phone, email, park.id())
               .thenAccept(res -> {
                    registerBtn.setText("✚  Register Guide");
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
        parkCombo.getSelectionModel().selectFirst();
    }
}
