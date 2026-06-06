package server.net;

import common.dto.ClientRequest;
import common.dto.ServerResponse;

public class ServerMessageHandler {
    private RequestRouter router = new RequestRouter();
    public ServerResponse handle(ClientRequest request) { return router.handle(request); }
}
