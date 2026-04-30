package server.dao;

import server.db.DBConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

public class VisitDAO {

    public int registerEntry(String reservationId, int parkId, int visitorsCount) {
        String sql = "INSERT INTO visits (reservation_id, park_id, entry_time, visitors_count) " +
                     "VALUES (?, ?, NOW(), ?)";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql, PreparedStatement.RETURN_GENERATED_KEYS)) {

            stmt.setString(1, reservationId);
            stmt.setInt(2, parkId);
            stmt.setInt(3, visitorsCount);

            stmt.executeUpdate();

            ResultSet rs = stmt.getGeneratedKeys();

            if (rs.next()) {
                int visitId = rs.getInt(1);
                System.out.println("Entry saved to DB! Visit ID: " + visitId);
                return visitId;
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        return -1;
    }

    public void registerExitByReservationId(String reservationId) {
        String sql = "UPDATE visits SET exit_time = NOW() " +
                     "WHERE reservation_id = ? AND exit_time IS NULL";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, reservationId);

            int rows = stmt.executeUpdate();

            if (rows > 0) {
                System.out.println("Exit saved to DB!");
            } else {
                System.out.println("No active visit found for reservation ID: " + reservationId);
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}