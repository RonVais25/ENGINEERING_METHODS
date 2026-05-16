package client.net;

import common.dto.ClientRequest;
import common.dto.ServerResponse;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;

/**
 * Persistent TCP connection to the GoNature server.
 *
 * Lifecycle:
 *   1. construct ClientConnection(host, port)
 *   2. connect()       — opens the socket and the object streams
 *   3. sendRequest(..) — many times, over the same socket
 *   4. close()         — on application shutdown
 *
 * Thread-safe: sendRequest is synchronized so the JavaFX background tasks
 * can't interleave writes on the same stream.
 */
public class ClientConnection {

    private static final int CONNECT_TIMEOUT_MS = 3000;
    private static final int READ_TIMEOUT_MS    = 5000;

    private final String host;
    private final int    port;

    private Socket socket;
    private ObjectOutputStream out;
    private ObjectInputStream  in;

    public ClientConnection(String host, int port) {
        this.host = host;
        this.port = port;
    }

    /** Open the socket and the object streams. Throws on failure. */
    public synchronized void connect() throws IOException {
        socket = new Socket();
        socket.connect(new InetSocketAddress(host, port), CONNECT_TIMEOUT_MS);
        socket.setSoTimeout(READ_TIMEOUT_MS);
        out = new ObjectOutputStream(socket.getOutputStream());
        out.flush();
        in  = new ObjectInputStream(socket.getInputStream());
    }

    public synchronized boolean isConnected() {
        return socket != null && socket.isConnected() && !socket.isClosed();
    }

    public synchronized ServerResponse sendRequest(ClientRequest request) {
        if (!isConnected()) {
            return new ServerResponse(false, "Not connected");
        }
        try {
            out.writeObject(request);
            out.flush();
            // Clear the stream's reference cache so the server-side ObjectInputStream
            // doesn't return stale data when a DTO with the same identity is sent.
            out.reset();
            return (ServerResponse) in.readObject();
        } catch (Exception e) {
            // The socket is unusable now — close it so isConnected() reports
            // false and the GUI status flips to Disconnected immediately.
            closeQuietly();
            return new ServerResponse(false, "Connection lost: " + e.getMessage());
        }
    }

    private void closeQuietly() {
        try { if (socket != null) socket.close(); } catch (Exception ignored) {}
        socket = null;
        out = null;
        in  = null;
    }

    public synchronized void close() {
        try { if (socket != null) socket.close(); } catch (Exception ignored) {}
        socket = null;
        out = null;
        in  = null;
    }
}
