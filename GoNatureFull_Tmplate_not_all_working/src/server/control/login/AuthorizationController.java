package server.control.login;

public class AuthorizationController {
    public boolean isAllowed(String role, String action) {
        return role != null && action != null;
    }
}
