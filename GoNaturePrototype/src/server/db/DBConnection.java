package server.db;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class DBConnection {

    private static final String URL =
            "jdbc:mysql://localhost:3306/gonature?serverTimezone=Asia/Jerusalem&useSSL=false";

    private static final String USER = "root";
    private static final String PASSWORD = "Aa123456";

    public static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(URL, USER, PASSWORD);
    }
}