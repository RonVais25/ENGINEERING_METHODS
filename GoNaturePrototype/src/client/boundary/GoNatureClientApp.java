package client.boundary;

import client.app.Session;
import client.service.NetworkService;
import client.view.LoginController;
import client.view.MainShellController;
import client.view.UserLoginController;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import java.net.URL;

/**
 * Application entry point for the client. Owns the Session and the
 * NetworkService and drives the screen flow:
 *
 * <pre>
 *   connection screen (utility stage) → user-login screen → main shell
 *                                            ↑__________________|
 *                                                  (logout)
 * </pre>
 *
 * The connection screen is a small separate stage; the user-login screen and
 * the main shell both render on the primary stage, swapping scenes. All visual
 * styling lives in client.css.
 */
public class GoNatureClientApp extends Application {

    /** Classpath path to the single shared stylesheet. */
    public static final String CSS_PATH = "/client/resources/client.css";

    private final Session session = new Session();
    private final NetworkService network = new NetworkService(session);

    /** Primary stage, reused for the user-login screen and the main shell. */
    private Stage mainStage;

    @Override
    public void start(Stage mainStage) throws Exception {
        this.mainStage = mainStage;
        showConnect();
    }

    @Override
    public void stop() {
        session.closeConnection();
    }

    public static void main(String[] args) { launch(args); }

    // TODO: streamline first-run UX — fold the connection step into the login
    // screen (one combined "connect + sign in" form, or auto-connect to a
    // configured/last-used server) so users don't face two separate windows.
    /** Step 1: small connection window — probe host:port, then hand off to login. */
    private void showConnect() throws Exception {
        Stage connectStage = new Stage();
        connectStage.setTitle("Connect to GoNature Server");
        connectStage.initStyle(StageStyle.UTILITY);
        connectStage.setResizable(false);

        FXMLLoader loader = new FXMLLoader(resource("/client/view/LoginView.fxml"));
        loader.setControllerFactory(type -> new LoginController(network, session, () -> {
            connectStage.close();
            showUserLogin();
        }));
        Parent root = loader.load();

        Scene scene = new Scene(root, 320, 320);
        scene.getStylesheets().add(stylesheet());
        connectStage.setScene(scene);
        connectStage.show();
    }

    /**
     * Step 2: identity login on the primary stage. Reused on logout, so it
     * always starts from a clean {@link Session} identity (the caller clears it).
     */
    private void showUserLogin() {
        try {
            FXMLLoader loader = new FXMLLoader(resource("/client/view/UserLoginView.fxml"));
            loader.setControllerFactory(type -> new UserLoginController(network, session, this::showMain));
            Parent root = loader.load();

            Scene scene = new Scene(root, 420, 560);
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

    /** Step 3: the role-aware main shell. Logout returns to the user-login screen. */
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

    private URL resource(String path) {
        URL url = getClass().getResource(path);
        if (url == null) throw new IllegalStateException("Missing classpath resource: " + path);
        return url;
    }

    private String stylesheet() {
        return getClass().getResource(CSS_PATH).toExternalForm();
    }
}
