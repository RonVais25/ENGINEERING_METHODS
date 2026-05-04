package server.net;

import common.dto.ClientRequest;
import common.dto.ServerResponse;

import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.ServerSocket;
import java.net.Socket;

public class OrderServer {

    private int port = 5555;
    private RequestRouter router = new RequestRouter();

    public void start() {
        System.out.println("Server started on port " + port);

        try (ServerSocket serverSocket = new ServerSocket(port)) {

            while (true) {
                Socket clientSocket = serverSocket.accept();

                System.out.println("Client connected");
                System.out.println("IP: " + clientSocket.getInetAddress().getHostAddress());
                System.out.println("Host: " + clientSocket.getInetAddress().getHostName());

                new Thread(() -> handleClient(clientSocket)).start();
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void handleClient(Socket socket) {
        try (
                ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
                ObjectInputStream in = new ObjectInputStream(socket.getInputStream())
        ) {

            ClientRequest request = (ClientRequest) in.readObject();
            ServerResponse response = router.handle(request);

            out.writeObject(response);
            out.flush();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}