package server.dao;

import common.dto.OrderDTO;
import server.db.DBConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

public class OrderDAO {

    public OrderDTO getOrderByNumber(int orderNumber) {
        String sql = "SELECT * FROM orders WHERE order_number = ?";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, orderNumber);
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                return new OrderDTO(
                        rs.getInt("order_number"),
                        rs.getString("order_date"),
                        rs.getInt("number_of_visitors"),
                        rs.getInt("confirmation_code"),
                        rs.getInt("subscriber_id"),
                        rs.getString("date_of_placing_order")
                );
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        return null;
    }

    public boolean updateOrder(int orderNumber, String newDate, int newVisitors) {
        String sql = "UPDATE orders SET order_date = ?, number_of_visitors = ? WHERE order_number = ?";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, newDate);
            stmt.setInt(2, newVisitors);
            stmt.setInt(3, orderNumber);

            int rows = stmt.executeUpdate();
            System.out.println("Rows updated: " + rows);

            return rows > 0;

        } catch (Exception e) {
            e.printStackTrace();
        }

        return false;
    }
}