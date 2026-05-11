package server.net;

import common.dto.ClientRequest;
import common.dto.ServerResponse;

import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.ServerSocket;
import java.net.Socket;

public class OrderServer {

    private final int port;
    private final ServerListener listener;
    private final RequestRouter router = new RequestRouter();

    private ServerSocket serverSocket;
    private volatile boolean running;

    public OrderServer(int port, ServerListener listener) {
        this.port = port;
        this.listener = listener;
    }

    public void start() {
        new Thread(this::runAcceptLoop, "OrderServer-accept").start();
    }

    public void stop() {
        running = false;
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (Exception e) {
            listener.onError("Error closing server: " + e.getMessage());
        }
    }

    private void runAcceptLoop() {
        try {
            serverSocket = new ServerSocket(port);
            running = true;
            listener.onStarted(port);

            while (running) {
                Socket clientSocket = serverSocket.accept();

                String ip   = clientSocket.getInetAddress().getHostAddress();
                String host = clientSocket.getInetAddress().getHostName();
                listener.onClientConnected(ip, host);

                new Thread(() -> handleClient(clientSocket, ip, host),
                           "OrderServer-client-" + ip).start();
            }

        } catch (Exception e) {
            if (running) {
                listener.onError("Server error: " + e.getMessage());
            }
        } finally {
            running = false;
            listener.onStopped();
        }
    }

    private void handleClient(Socket socket, String ip, String host) {
        try (ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
             ObjectInputStream  in  = new ObjectInputStream(socket.getInputStream())) {

            ClientRequest request = (ClientRequest) in.readObject();
            listener.onLog(ip + " → " + request.getType());

            ServerResponse response = router.handle(request);
            out.writeObject(response);
            out.flush();

            listener.onLog(ip + " ← " + (response.isSuccess() ? "OK" : "FAIL") +
                           " (" + response.getMessage() + ")");

        } catch (Exception e) {
            listener.onError("Client " + ip + " error: " + e.getMessage());
        } finally {
            listener.onClientDisconnected(ip, host);
        }
    }
}
