package server.dao;

import common.dto.ReservationDTO;
import server.db.DBConnection;
import java.sql.*;

public class ReservationDAO {
    public ReservationDTO createReservation(int visitorId, int parkId, String date, String time, int count, String visitorType) {
        String sql = "INSERT INTO reservations (visitor_id, park_id, visit_date, arrival_time, number_of_visitors, visitor_type, status, qr_code) VALUES (?,?,?,?,?,?, 'APPROVED', ?)";
        try (Connection conn = DBConnection.getConnection(); PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            stmt.setInt(1, visitorId); stmt.setInt(2, parkId); stmt.setString(3, date); stmt.setString(4, time); stmt.setInt(5, count); stmt.setString(6, visitorType);
            stmt.setString(7, "PENDING");
            stmt.executeUpdate();
            ResultSet keys = stmt.getGeneratedKeys();
            if (keys.next()) {
                int id = keys.getInt(1);
                String qr = "QR-" + id;
                try (PreparedStatement upd = conn.prepareStatement("UPDATE reservations SET qr_code=? WHERE reservation_id=?")) { upd.setString(1, qr); upd.setInt(2, id); upd.executeUpdate(); }
                return findById(id);
            }
        } catch (Exception e) { e.printStackTrace(); }
        return null;
    }

    public ReservationDTO findById(int reservationId) {
        String sql = "SELECT reservation_id, visitor_id, park_id, visit_date, arrival_time, number_of_visitors, visitor_type, status, qr_code FROM reservations WHERE reservation_id=?";
        try (Connection conn = DBConnection.getConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, reservationId);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) return map(rs);
        } catch (Exception e) { e.printStackTrace(); }
        return null;
    }

    public boolean updateReservation(int reservationId, String newDate, int newCount) {
        String sql = "UPDATE reservations SET visit_date=?, number_of_visitors=? WHERE reservation_id=?";
        try (Connection conn = DBConnection.getConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, newDate); stmt.setInt(2, newCount); stmt.setInt(3, reservationId);
            return stmt.executeUpdate() > 0;
        } catch (Exception e) { e.printStackTrace(); }
        return false;
    }

    public boolean updateStatus(int reservationId, String status) {
        String sql = "UPDATE reservations SET status=?, cancelled_at=CASE WHEN ?='CANCELLED' THEN NOW() ELSE cancelled_at END WHERE reservation_id=?";
        try (Connection conn = DBConnection.getConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, status); stmt.setString(2, status); stmt.setInt(3, reservationId);
            return stmt.executeUpdate() > 0;
        } catch (Exception e) { e.printStackTrace(); }
        return false;
    }

    private ReservationDTO map(ResultSet rs) throws SQLException {
        return new ReservationDTO(rs.getInt("reservation_id"), rs.getInt("visitor_id"), rs.getInt("park_id"), rs.getString("visit_date"), rs.getString("arrival_time"), rs.getInt("number_of_visitors"), rs.getString("visitor_type"), rs.getString("status"), rs.getString("qr_code"));
    }
}
