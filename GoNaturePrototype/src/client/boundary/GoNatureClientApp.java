package client.boundary;

import client.app.Session;
import client.service.NetworkService;
import client.view.LoginController;
import client.view.MainShellController;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import java.net.URL;

/**
 * Application entry point for the refactored client. Owns the Session and the
 * NetworkService and is responsible for swapping between the login window and
 * the main shell scene. All visual styling lives in client.css
 */
public class GoNatureClientApp extends Application {

    /** Classpath path to the single shared stylesheet. */
    public static final String CSS_PATH = "/client/resources/client.css";

    private final Session session = new Session();
    private final NetworkService network = new NetworkService(session);

    @Override
    public void start(Stage mainStage) throws Exception {
        showLogin(mainStage);
    }

    @Override
    public void stop() {
        session.closeConnection();
    }

    public static void main(String[] args) { launch(args); }

    /** Open the small login window. */
    private void showLogin(Stage mainStage) throws Exception {
        Stage loginStage = new Stage();
        loginStage.setTitle("Connect to GoNature Server");
        loginStage.initStyle(StageStyle.UTILITY);
        loginStage.setResizable(false);

        FXMLLoader loader = new FXMLLoader(resource("/client/view/LoginView.fxml"));
        loader.setControllerFactory(type -> new LoginController(network, session, () -> {
            loginStage.close();
            try { showMain(mainStage); }
            catch (Exception ex) { ex.printStackTrace(); }
        }));
        Parent root = loader.load();

        Scene scene = new Scene(root, 320, 320);
        scene.getStylesheets().add(stylesheet());
        loginStage.setScene(scene);
        loginStage.show();
    }

    /** Build the main shell and show the dashboard. */
    private void showMain(Stage stage) throws Exception {
        FXMLLoader loader = new FXMLLoader(resource("/client/view/MainShell.fxml"));
        loader.setControllerFactory(type -> new MainShellController(network, session));
        Parent root = loader.load();

        Scene scene = new Scene(root, 1000, 640);
        scene.getStylesheets().add(stylesheet());
        stage.setScene(scene);
        stage.setTitle("GoNature Park System");
        stage.setMinWidth(900);
        stage.setMinHeight(600);
        stage.show();
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
