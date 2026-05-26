package server.app;

import javafx.application.Application;
import server.boundary.ServerGUI;
import server.db.DotEnv;

public class StartServer {

    public static void main(String[] args) {
        DotEnv.load();
        Application.launch(ServerGUI.class, args);
    }
}
