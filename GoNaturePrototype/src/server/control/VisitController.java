package server.control;

import java.util.Set;

import common.dto.ClientRequest;
import common.dto.RequestType;
import common.dto.ServerResponse;
import server.net.ClientSession;

import static common.dto.RequestType.CASUAL_VISIT;
import static common.dto.RequestType.CURRENT_OCCUPANCY;
import static common.dto.RequestType.ENTER_VISIT;
import static common.dto.RequestType.EXIT_VISIT;

/**
 * Owns the visit domain (park entry/exit, casual walk-ins, live occupancy).
 * All ops are stubs pending the visit feature session.
 */
public class VisitController implements DomainController {

    @Override
    public Set<RequestType> handledTypes() {
        return Set.of(ENTER_VISIT, EXIT_VISIT, CASUAL_VISIT, CURRENT_OCCUPANCY);
    }

    @Override
    public ServerResponse handle(ClientRequest request, ClientSession session) {
        return new ServerResponse(false, "NOT_IMPLEMENTED: " + request.getType());
    }
}
