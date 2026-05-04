package server.app;

import server.net.OrderServer;

public class StartServer {

    public static void main(String[] args) {
        new OrderServer().start();
    }
}