package server.net;

/**
 * Callback interface through which the network layer reports its lifecycle and
 * activity to an observer — in practice the operator GUI in
 * {@link server.boundary}. The TCP server holds a single listener and invokes
 * these methods as the server starts and stops, as clients connect and
 * disconnect, and whenever there is activity or a failure to surface. Keeping
 * this as an interface lets the networking code stay free of any UI dependency.
 */
public interface ServerListener {

    /**
     * Called once the server has bound and begun accepting connections.
     *
     * @param port the TCP port the server is listening on
     */
    void onStarted(int port);

    /** Called once the server has stopped accepting connections and shut down. */
    void onStopped();

    /**
     * Called when a client establishes a connection.
     *
     * @param ip   the client's IP address
     * @param host the client's resolved host name (may equal the IP)
     */
    void onClientConnected(String ip, String host);

    /**
     * Called when a client's connection is closed or lost.
     *
     * @param ip   the client's IP address
     * @param host the client's resolved host name (may equal the IP)
     */
    void onClientDisconnected(String ip, String host);

    /**
     * Called to report a normal activity message for the operator's log.
     *
     * @param message the human-readable log line
     */
    void onLog(String message);

    /**
     * Called to report a failure that should stand out in the operator's log.
     *
     * @param message the human-readable error description
     */
    void onError(String message);
}
