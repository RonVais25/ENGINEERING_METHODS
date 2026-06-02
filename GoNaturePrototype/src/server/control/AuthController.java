package server.control;

import java.util.Set;

import common.dto.ClientRequest;
import common.dto.RequestType;
import common.dto.ServerResponse;
import server.net.ClientSession;

import static common.dto.RequestType.LOGIN_STAFF;
import static common.dto.RequestType.LOGIN_VISITOR;
import static common.dto.RequestType.LOGOUT;
import static common.dto.RequestType.REGISTER_GUIDE;
import static common.dto.RequestType.REGISTER_SUBSCRIBER;

/**
 * Owns the authentication / registration domain (login, logout, single-login
 * lock, subscriber & guide registration). All ops are stubs pending the auth
 * feature session.
 */
public class AuthController implements DomainController {

    @Override
    public Set<RequestType> handledTypes() {
        return Set.of(LOGIN_STAFF, LOGIN_VISITOR, LOGOUT, REGISTER_SUBSCRIBER, REGISTER_GUIDE);
    }

    @Override
    public ServerResponse handle(ClientRequest request, ClientSession session) {
        return new ServerResponse(false, "NOT_IMPLEMENTED: " + request.getType());
    }
}
