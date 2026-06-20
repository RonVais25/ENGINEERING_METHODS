package server.app;

import javafx.application.Application;
import server.boundary.ServerGUI;
import server.db.DotEnv;
/**
 * Represents the start server component of the GoNature application.
 */

public class StartServer {
/**
 * Application entry point.
 * @param args value supplied to the operation
 */

    public static void main(String[] args) {
        DotEnv.load();
        Application.launch(ServerGUI.class, args);
    }
}
