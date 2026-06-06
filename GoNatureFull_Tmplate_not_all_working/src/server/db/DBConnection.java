package server.db;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class DBConnection {
    public static Connection getConnection() throws SQLException {
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
        } catch (ClassNotFoundException e) {
            throw new SQLException("MySQL JDBC driver not found. Add mysql-connector-j to Build Path.", e);
        }
        return DriverManager.getConnection(DBConfig.URL, DBConfig.USER, DBConfig.PASSWORD);
    }
}
