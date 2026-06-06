package client.net;

import common.dto.ClientRequest;
import common.dto.ServerResponse;

public class GoNatureClient {
    private ClientConnection connection;
    public GoNatureClient(String host, int port) { connection = new ClientConnection(host, port); }
    public ServerResponse sendRequest(ClientRequest request) { return connection.send(request); }
}
