package server.net;

import common.dto.ClientRequest;
import common.dto.ServerResponse;

import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.ServerSocket;
import java.net.Socket;

public class GoNatureServer {

    private int port;
    private RequestRouter router = new RequestRouter();

    public GoNatureServer(int port) {
        this.port = port;
    }

    public void start() {
        System.out.println("GoNature Server started on port " + port);

        try (ServerSocket serverSocket = new ServerSocket(port)) {

            while (true) {
                Socket clientSocket = serverSocket.accept();

                System.out.println("Client connected.");
                System.out.println("Client IP: " + clientSocket.getInetAddress().getHostAddress());
                System.out.println("Client Host: " + clientSocket.getInetAddress().getHostName());
                System.out.println("Connection status: CONNECTED");

                new Thread(() -> handleClient(clientSocket)).start();
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void handleClient(Socket socket) {
        try (
                ObjectOutputStream output = new ObjectOutputStream(socket.getOutputStream());
                ObjectInputStream input = new ObjectInputStream(socket.getInputStream())
        ) {
            Object obj = input.readObject();

            ServerResponse response;

            if (obj instanceof ClientRequest) {
                ClientRequest request = (ClientRequest) obj;
                response = router.handle(request);
            } else {
                response = new ServerResponse(false, "Invalid request object.");
            }

            output.writeObject(response);
            output.flush();

        } catch (Exception e) {
            e.printStackTrace();

            try {
                ObjectOutputStream output = new ObjectOutputStream(socket.getOutputStream());
                output.writeObject(new ServerResponse(false, "Server error: " + e.getMessage()));
                output.flush();
            } catch (Exception ignored) {
            }
        }
    }
}