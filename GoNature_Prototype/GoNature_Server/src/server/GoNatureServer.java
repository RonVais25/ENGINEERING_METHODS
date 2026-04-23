package server;

import common.Message;
import common.Order;
import ocsf.server.AbstractServer;
import ocsf.server.ConnectionToClient;

import java.io.IOException;
import java.util.List;

/**
 * GoNatureServer extends OCSF's AbstractServer and routes
 * incoming Message objects to the DBController.
 *
 * IMPORTANT: This class contains ZERO SQL and ZERO JDBC.
 * All database work is delegated to DBController.
 */
public class GoNatureServer extends AbstractServer {

    private final DBController db;
    private final ServerObserver ui;

    public GoNatureServer(int port, DBController db, ServerObserver ui) {
        super(port);
        this.db = db;
        this.ui = ui;
    }

    @Override
    protected void handleMessageFromClient(Object msg, ConnectionToClient client) {
        if (!(msg instanceof Message)) {
            ui.log("Ignored non-Message object from " + client);
            return;
        }
        Message request = (Message) msg;
        ui.log("<< " + request.getCommand() + " from " + client.getInetAddress().getHostAddress());

        try {
            switch (request.getCommand()) {

                case "GET_ORDERS": {
                    List<Order> orders = db.getAllOrders();
                    client.sendToClient(new Message("ORDERS_LIST", orders));
                    ui.log(">> ORDERS_LIST (" + orders.size() + " rows) to "
                            + client.getInetAddress().getHostAddress());
                    break;
                }

                case "UPDATE_ORDER": {
                    Order toUpdate = (Order) request.getData();
                    boolean success = db.updateOrder(toUpdate);
                    client.sendToClient(new Message("UPDATE_RESULT", success));
                    ui.log(">> UPDATE_RESULT=" + success + " to "
                            + client.getInetAddress().getHostAddress());
                    break;
                }

                default: {
                    client.sendToClient(new Message("ERROR",
                            "Unknown command: " + request.getCommand()));
                    ui.log("!! unknown command: " + request.getCommand());
                }
            }
        } catch (IOException e) {
            ui.log("!! IO error responding to client: " + e.getMessage());
        } catch (ClassCastException e) {
            ui.log("!! bad payload: " + e.getMessage());
            try {
                client.sendToClient(new Message("ERROR", "Bad payload: " + e.getMessage()));
            } catch (IOException ignore) { /* client is gone */ }
        }
    }

    @Override
    protected synchronized void clientConnected(ConnectionToClient client) {
        String ip = client.getInetAddress().getHostAddress();
        String host = client.getInetAddress().getHostName();
        ui.addClient(ip, host, "Connected");
        ui.log("++ client connected " + ip + " (" + host + ")");
    }

    @Override
    protected synchronized void clientDisconnected(ConnectionToClient client) {
        String ip = client.getInetAddress().getHostAddress();
        ui.updateClientStatus(ip, "Disconnected");
        ui.log("-- client disconnected " + ip);
    }

    @Override
    protected synchronized void clientException(ConnectionToClient client, Throwable exception) {
        // Called when a client drops the connection abruptly.
        String ip = client.getInetAddress() != null
                ? client.getInetAddress().getHostAddress()
                : "unknown";
        ui.updateClientStatus(ip, "Disconnected");
        ui.log("!! client exception from " + ip + ": " + exception.getMessage());
    }

    @Override
    protected void serverStarted() {
        ui.log("** Server listening on port " + getPort());
    }

    @Override
    protected void serverStopped() {
        ui.log("** Server stopped");
    }
}
