package server.net;
/**
 * Contract for server listener behavior in the GoNature application.
 */

public interface ServerListener {
/**
 * Performs the on started operation.
 * @param port value supplied to the operation
 */

    void onStarted(int port);
/**
 * Performs the on stopped operation.
 */

    void onStopped();
/**
 * Performs the on client connected operation.
 * @param ip value supplied to the operation
 * @param host value supplied to the operation
 */

    void onClientConnected(String ip, String host);
/**
 * Performs the on client disconnected operation.
 * @param ip value supplied to the operation
 * @param host value supplied to the operation
 */

    void onClientDisconnected(String ip, String host);
/**
 * Performs the on log operation.
 * @param message value supplied to the operation
 */

    void onLog(String message);
/**
 * Performs the on error operation.
 * @param message value supplied to the operation
 */

    void onError(String message);
}
