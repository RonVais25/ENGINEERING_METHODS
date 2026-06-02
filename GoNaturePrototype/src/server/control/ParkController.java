package server.control;

import java.util.Set;

import common.dto.ClientRequest;
import common.dto.RequestType;
import common.dto.ServerResponse;
import server.net.ClientSession;

import static common.dto.RequestType.APPROVE_PARAM_CHANGE;
import static common.dto.RequestType.CHECK_AVAILABILITY;
import static common.dto.RequestType.GET_PARK;
import static common.dto.RequestType.LIST_PENDING_CHANGES;
import static common.dto.RequestType.REJECT_PARAM_CHANGE;
import static common.dto.RequestType.REQUEST_PARAM_CHANGE;

/**
 * Owns the park & parameter-change domain (park lookup, capacity availability,
 * parameter-change request/approve/reject workflow). All ops are stubs pending
 * the park feature session.
 */
public class ParkController implements DomainController {

    @Override
    public Set<RequestType> handledTypes() {
        return Set.of(GET_PARK, REQUEST_PARAM_CHANGE, LIST_PENDING_CHANGES, APPROVE_PARAM_CHANGE,
                REJECT_PARAM_CHANGE, CHECK_AVAILABILITY);
    }

    @Override
    public ServerResponse handle(ClientRequest request, ClientSession session) {
        return new ServerResponse(false, "NOT_IMPLEMENTED: " + request.getType());
    }
}
