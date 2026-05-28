package client.view;

import client.app.Session;
import client.service.NetworkService;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;

/**
 * Controller for LoginView.fxml. Probes the entered host:port with a PING;
 * on success, promotes the live connection into the {@link Session} and
 * runs the supplied onSuccess callback (which closes login + opens main).
 */
public class LoginController {

    @FXML private TextField hostField;
    @FXML private TextField portField;
    @FXML private Button    connectBtn;
    @FXML private Label     errorLabel;

    private final NetworkService network;
    private final Session        session;
    private final Runnable       onSuccess;

    public LoginController(NetworkService network, Session session, Runnable onSuccess) {
        this.network   = network;
        this.session   = session;
        this.onSuccess = onSuccess;
    }

    @FXML
    private void initialize() {
        hostField.setOnAction(e -> onConnect());
        portField.setOnAction(e -> onConnect());
    }

    @FXML
    private void onConnect() {
        String host = hostField.getText().trim();
        int port;
        try { port = Integer.parseInt(portField.getText().trim()); }
        catch (NumberFormatException ex) { showError("Port must be a number"); return; }

        connectBtn.setText("Connecting…");
        connectBtn.setDisable(true);
        hideError();

        network.probe(host, port).thenAccept(result -> {
            if (result.isSuccess()) {
                session.login(result.connection, host, port);
                onSuccess.run();
            } else {
                connectBtn.setText("→  Connect");
                connectBtn.setDisable(false);
                showError("Could not reach " + host + ":" + port);
            }
        });
    }

    private void showError(String msg) {
        errorLabel.setText(msg);
        errorLabel.setVisible(true);
        errorLabel.setManaged(true);
    }

    private void hideError() {
        errorLabel.setVisible(false);
        errorLabel.setManaged(false);
    }
}
