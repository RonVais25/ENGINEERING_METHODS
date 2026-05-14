package client.net;

import common.dto.ClientRequest;
import common.dto.ServerResponse;

import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;

public class ClientConnection {

    private static final int CONNECT_TIMEOUT_MS = 3000;
    private static final int READ_TIMEOUT_MS    = 5000;

    private final String host;
    private final int    port;

    public ClientConnection(String host, int port) {
        this.host = host;
        this.port = port;
    }

    public ServerResponse sendRequest(ClientRequest request) {

        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), CONNECT_TIMEOUT_MS);
            socket.setSoTimeout(READ_TIMEOUT_MS);

            try (ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
                 ObjectInputStream  in  = new ObjectInputStream(socket.getInputStream())) {

                out.writeObject(request);
                out.flush();

                return (ServerResponse) in.readObject();
            }

        } catch (Exception e) {
            return new ServerResponse(false, "Client error");
        }
    }
}
