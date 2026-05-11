package server.dao;

import common.dto.OrderDTO;
import server.db.DBConnection;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.util.concurrent.ThreadLocalRandom;

public class OrderDAO {

    public OrderDTO getOrderByNumber(int orderNumber) {
        String sql = "SELECT * FROM `Order` WHERE order_number = ?";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, orderNumber);
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                return new OrderDTO(
                        rs.getInt("order_number"),
                        rs.getDate("order_date").toString(),
                        rs.getInt("number_of_visitors"),
                        rs.getInt("confirmation_code"),
                        rs.getInt("subscriber_id"),
                        rs.getDate("date_of_placing_order").toString()
                );
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        return null;
    }

    public boolean updateOrder(int orderNumber, String newDate, int newVisitors) {
        String sql = "UPDATE `Order` SET order_date = ?, number_of_visitors = ? WHERE order_number = ?";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setDate(1, Date.valueOf(newDate));
            stmt.setInt(2, newVisitors);
            stmt.setInt(3, orderNumber);

            int rows = stmt.executeUpdate();
            return rows > 0;

        } catch (Exception e) {
            e.printStackTrace();
        }

        return false;
    }

    public OrderDTO insertOrder(String orderDate, int numberOfVisitors, int subscriberId) {
        String nextIdSql = "SELECT COALESCE(MAX(order_number), 1000) + 1 FROM `Order`";
        String insertSql = "INSERT INTO `Order` " +
                "(order_number, order_date, number_of_visitors, confirmation_code, subscriber_id, date_of_placing_order) " +
                "VALUES (?, ?, ?, ?, ?, ?)";

        try (Connection conn = DBConnection.getConnection()) {

            int nextId;
            try (PreparedStatement s = conn.prepareStatement(nextIdSql);
                 ResultSet rs = s.executeQuery()) {
                rs.next();
                nextId = rs.getInt(1);
            }

            int confirmationCode = ThreadLocalRandom.current().nextInt(1000, 10000);
            String placedOn = LocalDate.now().toString();

            try (PreparedStatement stmt = conn.prepareStatement(insertSql)) {
                stmt.setInt(1, nextId);
                stmt.setDate(2, Date.valueOf(orderDate));
                stmt.setInt(3, numberOfVisitors);
                stmt.setInt(4, confirmationCode);
                stmt.setInt(5, subscriberId);
                stmt.setDate(6, Date.valueOf(placedOn));

                if (stmt.executeUpdate() == 1) {
                    return new OrderDTO(nextId, orderDate, numberOfVisitors,
                                        confirmationCode, subscriberId, placedOn);
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        return null;
    }
}
