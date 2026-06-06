package server.net;

import common.dto.ClientRequest;
import common.dto.ServerResponse;
import server.util.ServerLog;

import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.ServerSocket;
import java.net.Socket;

public class GoNatureServer {
    private int port;
    private boolean running;
    private ClientConnectionRegistry registry = new ClientConnectionRegistry();
    private ServerMessageHandler handler = new ServerMessageHandler();

    public GoNatureServer(int port) { this.port = port; }

    public void start() {
        running = true;
        ServerLog.log("GoNature server started on port " + port);
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            while (running) {
                Socket client = serverSocket.accept();
                registry.add(client);
                ServerLog.log("Client connected. IP: " + client.getInetAddress().getHostAddress() + " Host: " + client.getInetAddress().getHostName() + " Status: CONNECTED");
                new Thread(() -> handleClient(client)).start();
            }
        } catch (Exception e) { ServerLog.log("Server error: " + e.getMessage()); e.printStackTrace(); }
    }

    private void handleClient(Socket socket) {
        try (ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream()); ObjectInputStream in = new ObjectInputStream(socket.getInputStream())) {
            Object obj = in.readObject();
            ServerResponse response = obj instanceof ClientRequest ? handler.handle((ClientRequest) obj) : new ServerResponse(false, "Invalid request object.");
            out.writeObject(response); out.flush();
        } catch (Exception e) { ServerLog.log("Client handling error: " + e.getMessage()); }
        finally { registry.remove(socket); }
    }
}
