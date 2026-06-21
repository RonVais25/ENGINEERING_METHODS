package server.app;

import javafx.application.Application;
import server.boundary.ServerGUI;
import server.db.DotEnv;
/**
 * Entry point of the server application.
 * Loads environment variables and starts the server GUI.
 */
public class StartServer {

    /** Creates the server launcher. */
    public StartServer() { }
	/**
     * Starts the server application.
     *
     * @param args command-line arguments
     */
    public static void main(String[] args) {
        DotEnv.load();
        Application.launch(ServerGUI.class, args);
    }
}
