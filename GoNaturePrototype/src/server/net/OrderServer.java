package server.net;

import common.dto.ClientRequest;
import common.dto.ServerResponse;
import server.subscription.SubscriptionRegistry;

import java.io.EOFException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class OrderServer {

    private final int port;
    private final ServerListener listener;
    private final RequestRouter router = new RequestRouter();

    private ServerSocket serverSocket;
    private volatile boolean running;

    // Every accepted client socket is tracked so stop() can force-close them.
    // Without this, server.stop() only stops accept() — existing handleClient
    // threads keep running and the clients can still send requests.
    private final List<Socket> activeClients = Collections.synchronizedList(new ArrayList<>());

    public OrderServer(int port, ServerListener listener) {
        this.port = port;
        this.listener = listener;
    }

    public void start() {
        Thread t = new Thread(this::runAcceptLoop, "OrderServer-accept");
        t.setDaemon(true);
        t.start();
    }

    public void stop() {
        running = false;
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (Exception e) {
            listener.onError("Error closing server: " + e.getMessage());
        }

        // Force-close every still-open client socket so the handleClient
        // threads unwind and the clients see the connection drop immediately.
        synchronized (activeClients) {
            for (Socket s : new ArrayList<>(activeClients)) {
                try { s.close(); } catch (Exception ignored) {}
            }
            activeClients.clear();
        }
    }

    private void runAcceptLoop() {
        try {
            serverSocket = new ServerSocket(port);
            running = true;
            listener.onStarted(port);

            while (running) {
                Socket clientSocket = serverSocket.accept();

                String ip   = clientSocket.getInetAddress().getHostAddress();
                String host = clientSocket.getInetAddress().getHostName();

                activeClients.add(clientSocket);
                listener.onClientConnected(ip, host);

                Thread t = new Thread(() -> handleClient(clientSocket, ip, host),
                                      "OrderServer-client-" + ip);
                t.setDaemon(true);
                t.start();
            }

        } catch (Exception e) {
            if (running) {
                listener.onError("Server error: " + e.getMessage());
            }
        } finally {
            running = false;
            listener.onStopped();
        }
    }

    private void handleClient(Socket socket, String ip, String host) {
        // Persistent connection: read requests in a loop until the client
        // closes its socket (EOF) or the connection drops. The session ends
        // only on disconnect, not after each request.
        //
        // Outbound writes (responses + future pushed events) go through the
        // ClientSession so they share one writeLock; interleaved writes on the
        // same ObjectOutputStream would produce garbled bytes on the wire.
        ClientSession session = null;
        ObjectOutputStream out = null;
        ObjectInputStream  in  = null;
        try {
            out = new ObjectOutputStream(socket.getOutputStream());
            out.flush();
            in  = new ObjectInputStream(socket.getInputStream());
            session = new ClientSession(socket, in, out);

            while (!socket.isClosed()) {
                ClientRequest request;
                try {
                    request = (ClientRequest) in.readObject();
                } catch (EOFException | SocketException eof) {
                    // Client closed the socket — normal disconnect, not an error.
                    break;
                }

                listener.onLog(ip + " → " + request.getType());
                listener.onLog("[req id=" + request.getCorrelationId() + "] type=" + request.getType());

                ServerResponse response = router.handle(request, session);
                // Echo the request's correlation id onto the response so the client
                // can match it back to the originating request once the reader-thread
                // routing lands in step 3 of the realtime push channel.
                response.setCorrelationId(request.getCorrelationId());
                session.sendResponse(response);

                listener.onLog(ip + " ← " + (response.isSuccess() ? "OK" : "FAIL") +
                               " (" + response.getMessage() + ")");
            }

        } catch (Exception e) {
            listener.onError("Client " + ip + " error: " + e.getMessage());
        } finally {
            if (session != null) {
                // Detach from every subscription bucket so the registry never
                // tries to push to this dead socket, then close streams+socket.
                SubscriptionRegistry.getInstance().unregisterAll(session);
                session.close();
            } else {
                // Session was never constructed (stream open failed). Close
                // whatever did get opened so we don't leak file descriptors.
                try { if (in  != null) in.close();  } catch (Exception ignored) {}
                try { if (out != null) out.close(); } catch (Exception ignored) {}
                try { socket.close(); } catch (Exception ignored) {}
            }
            activeClients.remove(socket);
            listener.onClientDisconnected(ip, host);
        }
    }
}
