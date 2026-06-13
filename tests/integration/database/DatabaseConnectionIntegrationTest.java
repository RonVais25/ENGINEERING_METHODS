package integration.database;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import server.db.DBConnection;

import java.sql.Connection;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Enable this class only on a machine that has:
 * 1. MySQL running.
 * 2. GoNature schema loaded.
 * 3. MySQL connector in the test classpath.
 */
@Disabled("Requires real MySQL database")
class DatabaseConnectionIntegrationTest {

    @Test
    void databaseConnectionShouldOpenSuccessfully() throws Exception {
        try (Connection connection = DBConnection.getConnection()) {
            assertNotNull(connection);
            assertFalse(connection.isClosed());
        }
    }

    @Test
    void databaseShouldBeGonature() throws Exception {
        try (Connection connection = DBConnection.getConnection()) {
            assertEquals("gonature", connection.getCatalog());
        }
    }
}
