package client.net;

import common.dto.ClientRequest;
import common.dto.ServerResponse;

import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;

public class ClientConnection {

    private String host;
    private int port;

    public ClientConnection(String host, int port) {
        this.host = host;
        this.port = port;
    }

    public ServerResponse sendRequest(ClientRequest request) {

        try (Socket socket = new Socket(host, port);
             ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
             ObjectInputStream in = new ObjectInputStream(socket.getInputStream())) {

            out.writeObject(request);
            out.flush();

            return (ServerResponse) in.readObject();

        } catch (Exception e) {
            e.printStackTrace();
            return new ServerResponse(false, "Client error");
        }
    }
}