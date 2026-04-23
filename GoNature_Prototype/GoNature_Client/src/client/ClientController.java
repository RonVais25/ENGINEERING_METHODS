package client;

import common.Message;
import common.Order;

import java.io.IOException;
import java.util.List;

/**
 * ClientController is the CONTROL layer in ECB.
 * It does NOT know about JavaFX widgets directly; it exposes callbacks
 * that ClientUI registers (Observer-style).
 *
 * This means the UI can be swapped out (e.g. later for a web front-end)
 * without rewriting the client's business logic.
 */
public class ClientController {

    private GoNatureClient networkClient;

    // --- Callback interfaces: the UI implements these ---

    public interface OrdersListener {
        void onOrdersReceived(List<Order> orders);
        void onUpdateResult(boolean success);
        void onError(String message);
        void onDisconnected();
    }

    private OrdersListener listener;

    public void setListener(OrdersListener listener) {
        this.listener = listener;
    }

    // --- Connection lifecycle ---

    public void connect(String host, int port) throws IOException {
        networkClient = new GoNatureClient(host, port, this);
    }

    public boolean isConnected() {
        return networkClient != null && networkClient.isConnected();
    }

    public void disconnect() {
        if (networkClient != null) {
            try { networkClient.closeConnection(); }
            catch (IOException ignore) { /* best-effort */ }
        }
    }

    // --- Operations the UI can trigger ---

    public void requestOrders() {
        if (networkClient != null) networkClient.send("GET_ORDERS", null);
    }

    public void updateOrder(Order order) {
        if (networkClient != null) networkClient.send("UPDATE_ORDER", order);
    }

    // --- Server responses, dispatched to the UI ---

    @SuppressWarnings("unchecked")
    void handleResponse(Message response) {
        if (listener == null) return;
        switch (response.getCommand()) {
            case "ORDERS_LIST":
                listener.onOrdersReceived((List<Order>) response.getData());
                break;
            case "UPDATE_RESULT":
                listener.onUpdateResult((Boolean) response.getData());
                break;
            case "ERROR":
                listener.onError(String.valueOf(response.getData()));
                break;
            default:
                listener.onError("Unknown response: " + response.getCommand());
        }
    }

    void onDisconnected() {
        if (listener != null) listener.onDisconnected();
    }

    void onError(String msg) {
        if (listener != null) listener.onError(msg);
    }
}
