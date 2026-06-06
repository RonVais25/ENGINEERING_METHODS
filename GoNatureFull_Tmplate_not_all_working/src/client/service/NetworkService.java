package client.service;

import client.net.GoNatureClient;
import common.dto.ClientRequest;
import common.dto.ServerResponse;

public class NetworkService {
    private GoNatureClient client;
    public void connect(String host, int port) { client = new GoNatureClient(host, port); }
    public ServerResponse send(ClientRequest request) { return client.sendRequest(request); }
}
