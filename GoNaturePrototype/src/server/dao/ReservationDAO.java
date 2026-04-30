package server.dao;

import server.db.DBConnection;
import common.dto.ReservationDTO;
import java.sql.ResultSet;
import java.sql.Connection;
import java.sql.PreparedStatement;

public class ReservationDAO {

    public void saveReservation(String reservationId, int visitorId, int parkId,
                                String date, String time, int numVisitors) {

        String sql = "INSERT INTO reservations " +
                "(reservation_id, visitor_id, park_id, visit_date, arrival_time, number_of_visitors, status) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?)";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, reservationId);
            stmt.setInt(2, visitorId);
            stmt.setInt(3, parkId);
            stmt.setString(4, date);
            stmt.setString(5, time);
            stmt.setInt(6, numVisitors);
            stmt.setString(7, "APPROVED");

            stmt.executeUpdate();

            System.out.println("Reservation saved to DB!");

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    public ReservationDTO getReservationById(String reservationId) {
        String sql = "SELECT reservation_id, visitor_id, park_id, visit_date, arrival_time, number_of_visitors, status " +
                     "FROM reservations WHERE reservation_id = ?";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, reservationId);

            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                return new ReservationDTO(
                        rs.getString("reservation_id"),
                        rs.getInt("visitor_id"),
                        rs.getInt("park_id"),
                        rs.getString("visit_date"),
                        rs.getString("arrival_time"),
                        rs.getInt("number_of_visitors"),
                        rs.getString("status")
                );
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        return null;
    }

    public boolean updateReservation(String reservationId, String newDate, int newNumberOfVisitors) {
        String sql = "UPDATE reservations SET visit_date = ?, number_of_visitors = ? WHERE reservation_id = ?";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, newDate);
            stmt.setInt(2, newNumberOfVisitors);
            stmt.setString(3, reservationId);

            int rows = stmt.executeUpdate();

            return rows > 0;

        } catch (Exception e) {
            e.printStackTrace();
        }

        return false;
    }
}