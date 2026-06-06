package server.control;

import java.util.Set;

import common.dto.ClientRequest;
import common.dto.RequestType;
import common.dto.ServerResponse;
import server.net.ClientSession;

/**
 * A handler for one domain's slice of the server's request space (orders,
 * reservations, parks, …). {@code RequestRouter} owns one instance of each
 * controller and dispatches every incoming {@link ClientRequest} to the
 * controller that {@linkplain #handledTypes() claims} its {@link RequestType}.
 *
 * <p><strong>Threading:</strong> a single instance of each controller is shared
 * across all client-handling threads (one router, built once in
 * {@code OrderServer}). Implementations MUST therefore be stateless — hold only
 * immutable collaborators such as DAOs as fields, never per-request mutable
 * state, or two concurrent clients will corrupt each other.
 */
public interface DomainController {

    /**
     * The set of operation types this controller is responsible for. The router
     * builds its dispatch map from these sets at construction time; if two
     * controllers return overlapping sets the router fails fast.
     *
     * @return the request types handled by this controller; should be a stable,
     *         immutable set (the router treats it as fixed)
     */
    Set<RequestType> handledTypes();

    /**
     * Handles one request and produces the response to send back. Called
     * concurrently from multiple client threads, so this method must rely only
     * on its arguments and immutable fields (see the threading note on the
     * interface). The correlation id is stamped centrally by {@code OrderServer}
     * before the response is sent, so implementations must NOT set it here.
     *
     * @param request the incoming request; its {@link RequestType} is guaranteed
     *                to be one of this controller's {@link #handledTypes()}
     * @param session the originating client's session (for subscription-aware ops)
     * @return the response to return to the client; never {@code null}
     */
    ServerResponse handle(ClientRequest request, ClientSession session);
}
