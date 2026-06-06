package server.app;

import server.net.GoNatureServer;

public class StartServer {
    public static void main(String[] args) {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : 5555;
        new GoNatureServer(port).start();
    }
}
