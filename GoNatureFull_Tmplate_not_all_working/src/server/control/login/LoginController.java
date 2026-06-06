package server.control.login;

public class LoginController {
    public boolean login(String username, String password) {
        return username != null && !username.isEmpty() && password != null && !password.isEmpty();
    }
}
