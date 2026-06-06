package client.service;

public class ClientSessionService {
    private String currentUser;
    public String getCurrentUser() { return currentUser; }
    public void setCurrentUser(String currentUser) { this.currentUser = currentUser; }
}
