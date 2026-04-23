package client;

import common.Message;
import ocsf.client.AbstractClient;

import java.io.IOException;

/**
 * Thin OCSF client.
 * Responsible ONLY for sending/receiving messages.
 * All UI logic lives in ClientController + ClientUI.
 */
public class GoNatureClient extends AbstractClient {

    private final ClientController controller;

    public GoNatureClient(String host, int port, ClientController controller) throws IOException {
        super(host, port);
        this.controller = controller;
        openConnection();                       // throws IOException if no server
    }

    @Override
    protected void handleMessageFromServer(Object msg) {
        if (msg instanceof Message) {
            controller.handleResponse((Message) msg);
        } else {
            System.err.println("[Client] Unexpected object from server: " + msg);
        }
    }

    @Override
    protected void connectionClosed() {
        controller.onDisconnected();
    }

    @Override
    protected void connectionException(Exception exception) {
        controller.onError("Connection lost: " + exception.getMessage());
    }

    /**
     * Convenience: send a Message with the given command and payload.
     */
    public void send(String command, Object data) {
        try {
            sendToServer(new Message(command, data));
        } catch (IOException e) {
            controller.onError("Failed to send: " + e.getMessage());
        }
    }
}
