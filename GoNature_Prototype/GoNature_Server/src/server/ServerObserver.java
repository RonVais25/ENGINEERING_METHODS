package server;

/**
 * Thin callback interface implemented by ServerUI.
 *
 * Why: OCSF's GoNatureServer should NOT depend on JavaFX.
 * Exposing these callbacks keeps the server reusable even if
 * the UI is replaced (e.g. later by a web/REST front-end).
 */
public interface ServerObserver {
    void addClient(String ip, String host, String status);
    void updateClientStatus(String ip, String status);
    void log(String line);
}
