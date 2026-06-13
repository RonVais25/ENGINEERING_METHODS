package integration.database;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import server.db.DBConnection;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Set;
import java.util.HashSet;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Checks that the real MySQL schema contains the tables needed by the final project.
 */
@Disabled("Requires real MySQL database with schema.sql loaded")
class DatabaseSchemaIntegrationTest {

    @Test
    void requiredTablesShouldExist() throws Exception {
        Set<String> expectedTables = Set.of(
                "users",
                "parks",
                "visitors",
                "family_subscriptions",
                "authorized_guides",
                "reservations",
                "waiting_list",
                "visits",
                "bills",
                "payments",
                "promotions",
                "notifications",
                "generated_reports",
                "reservation_cancellations"
        );

        Set<String> actualTables = new HashSet<>();

        try (Connection connection = DBConnection.getConnection();
             ResultSet rs = connection.getMetaData().getTables(connection.getCatalog(), null, "%", new String[]{"TABLE"})) {

            while (rs.next()) {
                actualTables.add(rs.getString("TABLE_NAME"));
            }
        }

        assertTrue(actualTables.containsAll(expectedTables),
                "Missing tables: " + expectedTables.stream().filter(t -> !actualTables.contains(t)).toList());
    }

    @Test
    void seedDataShouldContainAtLeastOnePark() throws Exception {
        try (Connection connection = DBConnection.getConnection();
             Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("SELECT COUNT(*) FROM parks")) {

            assertTrue(rs.next());
            assertTrue(rs.getInt(1) > 0);
        }
    }

    @Test
    void seedDataShouldContainDemoUsers() throws Exception {
        try (Connection connection = DBConnection.getConnection();
             Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("SELECT COUNT(*) FROM users WHERE username IN ('traveler','worker','manager','department','service')")) {

            assertTrue(rs.next());
            assertTrue(rs.getInt(1) >= 4);
        }
    }
}
