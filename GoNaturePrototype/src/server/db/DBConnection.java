package server.db;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class DBConnection {

    private static final String URL =
            "jdbc:mysql://127.0.0.1:3306/gonature" +
            "?serverTimezone=Asia/Jerusalem" +
            "&useSSL=false" +
            "&allowPublicKeyRetrieval=true";

    private static final String USER = "root";

    private static String password = resolveInitialPassword();

    private static String resolveInitialPassword() {
        String env = System.getenv("DB_PASSWORD");
        if (env != null) return env;
        String prop = System.getProperty("DB_PASSWORD");
        if (prop != null) return prop;
        return "";
    }

    public static void setPassword(String pw) {
        password = pw != null ? pw : "";
    }

    public static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(URL, USER, password);
    }
}
