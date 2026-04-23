package server;

import common.Order;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * DBController encapsulates ALL database access.
 * No other class in the project should talk to JDBC directly.
 * This respects the requirement: "Queries must be placed in the correct class".
 */
public class DBController {

    private Connection connection;
    private final String url;
    private final String user;
    private final String password;

    public DBController(String url, String user, String password) {
        this.url = url;
        this.user = user;
        this.password = password;
    }

    /**
     * Loads the MySQL driver and opens a JDBC connection.
     * Call this once when the server starts.
     */
    public boolean connect() {
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
            connection = DriverManager.getConnection(url, user, password);
            System.out.println("[DB] Connected to " + url);
            return true;
        } catch (ClassNotFoundException e) {
            System.err.println("[DB] MySQL driver not found on classpath: " + e.getMessage());
        } catch (SQLException e) {
            System.err.println("[DB] Connection failed: " + e.getMessage());
        }
        return false;
    }

    public void disconnect() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
                System.out.println("[DB] Connection closed");
            }
        } catch (SQLException e) {
            System.err.println("[DB] Error closing connection: " + e.getMessage());
        }
    }

    /**
     * Read all rows from the Order table.
     * Used for the client's "Load Orders" action.
     */
    public List<Order> getAllOrders() {
        List<Order> orders = new ArrayList<>();
        String sql = "SELECT order_number, order_date, number_of_visitors, "
                   + "confirmation_code, subscriber_id, date_of_placing_order "
                   + "FROM `Order`";
        try (Statement st = connection.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                orders.add(new Order(
                        rs.getInt("order_number"),
                        rs.getDate("order_date"),
                        rs.getInt("number_of_visitors"),
                        rs.getInt("confirmation_code"),
                        rs.getInt("subscriber_id"),
                        rs.getDate("date_of_placing_order")
                ));
            }
        } catch (SQLException e) {
            System.err.println("[DB] getAllOrders error: " + e.getMessage());
        }
        return orders;
    }

    /**
     * Update order_date and number_of_visitors for an existing order.
     * Returns true if exactly one row was updated.
     */
    public boolean updateOrder(Order order) {
        String sql = "UPDATE `Order` "
                   + "SET order_date = ?, number_of_visitors = ? "
                   + "WHERE order_number = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setDate(1, order.getOrderDate());
            ps.setInt(2, order.getNumberOfVisitors());
            ps.setInt(3, order.getOrderNumber());
            int rows = ps.executeUpdate();
            System.out.println("[DB] updateOrder affected rows = " + rows);
            return rows == 1;
        } catch (SQLException e) {
            System.err.println("[DB] updateOrder error: " + e.getMessage());
            return false;
        }
    }
}
