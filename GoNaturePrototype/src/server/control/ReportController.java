package server.control;

import java.util.Set;

import common.dto.ClientRequest;
import common.dto.RequestType;
import common.dto.ServerResponse;
import server.net.ClientSession;

import static common.dto.RequestType.REPORT_CANCELLATIONS;
import static common.dto.RequestType.REPORT_VISITS_BY_TYPE;

/**
 * Owns the reporting domain (visits-by-type and cancellation reports).
 * All ops are stubs pending the reports feature session.
 */
public class ReportController implements DomainController {

    @Override
    public Set<RequestType> handledTypes() {
        return Set.of(REPORT_VISITS_BY_TYPE, REPORT_CANCELLATIONS);
    }

    @Override
    public ServerResponse handle(ClientRequest request, ClientSession session) {
        return new ServerResponse(false, "NOT_IMPLEMENTED: " + request.getType());
    }
}
