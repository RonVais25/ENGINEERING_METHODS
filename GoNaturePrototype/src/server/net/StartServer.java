package server.net;

public class StartServer {

    public static void main(String[] args) {
        GoNatureServer server = new GoNatureServer(555);
        server.start();
    }
}