package client.boundary;

import client.app.Session;
import client.service.NetworkService;
import client.view.MainShellController;
import client.view.UserLoginController;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import java.net.URL;

/**
 * Application entry point for the client. Owns the Session and the
 * NetworkService and drives the screen flow:
 *
 * <pre>
 *   user-login screen (connect + sign in) → main shell
 *                          ↑___________________|
 *                                 (logout)
 * </pre>
 *
 * The combined user-login screen collects the server host/port and the
 * credentials in one step (it PING-probes the server before authenticating);
 * it and the main shell both render on the primary stage, swapping scenes. All
 * visual styling lives in client.css.
 */
public class GoNatureClientApp extends Application {

    /** Classpath path to the single shared stylesheet. */
    public static final String CSS_PATH = "/client/resources/client.css";
/** Stores the session value used by this component. */

    private final Session session = new Session();
/** Stores the network value used by this component. */
    private final NetworkService network = new NetworkService(session);

    /** Primary stage, reused for the user-login screen and the main shell. */
    private Stage mainStage;
/**
 * Performs the start operation.
 * @param mainStage value supplied to the operation
 * @throws Exception if the operation cannot be completed
 */

    @Override
    public void start(Stage mainStage) throws Exception {
        this.mainStage = mainStage;
        showUserLogin();
    }
/**
 * Performs the stop operation.
 */

    @Override
    public void stop() {
        session.closeConnection();
    }
/**
 * Application entry point.
 */

    public static void main(String[] args) { launch(args); }

    /**
     * Step 1: the combined connect + sign-in screen on the primary stage. It
     * collects the server host/port together with the credentials and PING-probes
     * the server before authenticating. Reused on logout, so it always starts from
     * a clean {@link Session} identity (the caller clears it); the live connection
     * is kept across logout, so a re-login reuses it instead of re-probing.
     */
    private void showUserLogin() {
        try {
            FXMLLoader loader = new FXMLLoader(resource("/client/view/UserLoginView.fxml"));
            loader.setControllerFactory(type -> new UserLoginController(network, session, this::showMain));
            Parent root = loader.load();

            Scene scene = new Scene(root, 420, 600);
            scene.getStylesheets().add(stylesheet());
            mainStage.setResizable(false);
            mainStage.setScene(scene);
            mainStage.setTitle("GoNature — Sign In");
            mainStage.centerOnScreen();
            mainStage.show();
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    /** Step 2: the role-aware main shell. Logout returns to the user-login screen. */
    private void showMain() {
        try {
            FXMLLoader loader = new FXMLLoader(resource("/client/view/MainShell.fxml"));
            loader.setControllerFactory(type -> new MainShellController(network, session, this::showUserLogin));
            Parent root = loader.load();

            Scene scene = new Scene(root, 1000, 640);
            scene.getStylesheets().add(stylesheet());
            mainStage.setResizable(true);
            mainStage.setScene(scene);
            mainStage.setTitle("GoNature Park System");
            mainStage.setMinWidth(900);
            mainStage.setMinHeight(600);
            mainStage.centerOnScreen();
            mainStage.show();
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }
/**
 * Performs the resource operation.
 * @param path value supplied to the operation
 * @return the result produced by the operation
 */

    private URL resource(String path) {
        URL url = getClass().getResource(path);
        if (url == null) throw new IllegalStateException("Missing classpath resource: " + path);
        return url;
    }
/**
 * Performs the stylesheet operation.
 * @return the result produced by the operation
 */

    private String stylesheet() {
        return getClass().getResource(CSS_PATH).toExternalForm();
    }
}
