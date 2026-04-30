package client.net;

import common.dto.ClientRequest;
import common.dto.ServerResponse;

import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;

public class GoNatureClient {

    private String host;
    private int port;

    public GoNatureClient(String host, int port) {
        this.host = host;
        this.port = port;
    }

    public ServerResponse sendRequest(ClientRequest request) {

        try (
                Socket socket = new Socket(host, port);
                ObjectOutputStream output = new ObjectOutputStream(socket.getOutputStream());
                ObjectInputStream input = new ObjectInputStream(socket.getInputStream())
        ) {
            output.writeObject(request);
            output.flush();

            return (ServerResponse) input.readObject();

        } catch (Exception e) {
            e.printStackTrace();
            return new ServerResponse(false, "Client error: " + e.getMessage());
        }
    }
}