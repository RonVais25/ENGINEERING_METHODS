package server.db;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * Manages the connection to the system's MySQL database.
 */
public class DBConnection {

    /** Creates the database-connection helper. */
    public DBConnection() { }

    /** JDBC URL of the local MySQL {@code gonature} schema. */
    private static final String URL =
            "jdbc:mysql://127.0.0.1:3306/gonature" +
            "?serverTimezone=Asia/Jerusalem" +
            "&useSSL=false" +
            "&allowPublicKeyRetrieval=true";

    /** MySQL user the server connects as. */
    private static final String USER = "root";

    /** Current database password (from env/property, or set via {@link #setPassword(String)}). */
    private static String password = resolveInitialPassword();

    /**
     * Resolves the initial password from {@code DB_PASSWORD} (env then system
     * property), defaulting to empty.
     *
     * @return the initial database password, or {@code ""} if none is set
     */
    private static String resolveInitialPassword() {
        String env = System.getenv("DB_PASSWORD");
        if (env != null) return env;
        String prop = System.getProperty("DB_PASSWORD");
        if (prop != null) return prop;
        return "";
    }

    /**
     * Sets the database connection password.
     *
     * @param pw the new database password to be set
     */
    public static void setPassword(String pw) {
        password = pw != null ? pw : "";
    }

    /**
     * Creates and returns an active connection to the database.
     *
     * @return an active connection instance to the MySQL database
     * @throws SQLException if a database access error occurs
     */
    public static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(URL, USER, password);
    }
}