package client.app;

import client.net.ClientConnection;

/**
 * Holds connection details and the current logged-in subscriber for the
 * lifetime of the client process. One instance is created in
 * GoNatureClientApp and passed around via the FXMLLoader controller factory.
 */
public class Session {

    public static final int DEFAULT_SUBSCRIBER_ID = 42069;
    public static final String DEFAULT_USER_NAME  = "Ron V.";
    public static final String DEFAULT_INITIALS   = "RV";

    private ClientConnection connection;
    private String host;
    private int port;
    private int subscriberId = DEFAULT_SUBSCRIBER_ID;

    public ClientConnection getConnection() { return connection; }
    public String getHost()                 { return host; }
    public int getPort()                    { return port; }
    public int getSubscriberId()            { return subscriberId; }
    public String getUserName()             { return DEFAULT_USER_NAME; }
    public String getUserInitials()         { return DEFAULT_INITIALS; }

    public void login(ClientConnection conn, String host, int port) {
        this.connection = conn;
        this.host = host;
        this.port = port;
    }

    public void closeConnection() {
        if (connection != null) connection.close();
    }
}
