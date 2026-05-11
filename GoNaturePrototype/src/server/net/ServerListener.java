package server.net;

public interface ServerListener {

    void onStarted(int port);

    void onStopped();

    void onClientConnected(String ip, String host);

    void onClientDisconnected(String ip, String host);

    void onLog(String message);

    void onError(String message);
}
