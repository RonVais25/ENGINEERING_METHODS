package server.dao;

import common.dto.VisitDTO;
import server.db.DBConnection;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class VisitDAO {
    public int registerEntry(int reservationId, int parkId, int count, String entryType) {
        String sql = "INSERT INTO visits (reservation_id, park_id, entry_time, visitors_count, entry_type) VALUES (?, ?, NOW(), ?, ?)";
        try (Connection conn = DBConnection.getConnection(); PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            stmt.setInt(1, reservationId); stmt.setInt(2, parkId); stmt.setInt(3, count); stmt.setString(4, entryType); stmt.executeUpdate();
            new ParkDAO().addCurrentVisitors(conn, parkId, count);
            ResultSet keys = stmt.getGeneratedKeys();
            if (keys.next()) return keys.getInt(1);
        } catch (Exception e) { e.printStackTrace(); }
        return -1;
    }

    public boolean registerExit(int reservationId) {
        try (Connection conn = DBConnection.getConnection()) {
            int parkId = -1, count = 0;
            try (PreparedStatement q = conn.prepareStatement("SELECT park_id, visitors_count FROM visits WHERE reservation_id=? AND exit_time IS NULL ORDER BY visit_id DESC LIMIT 1")) {
                q.setInt(1, reservationId); ResultSet rs = q.executeQuery();
                if (rs.next()) { parkId = rs.getInt("park_id"); count = rs.getInt("visitors_count"); } else return false;
            }
            try (PreparedStatement stmt = conn.prepareStatement("UPDATE visits SET exit_time=NOW() WHERE reservation_id=? AND exit_time IS NULL")) {
                stmt.setInt(1, reservationId); int rows = stmt.executeUpdate();
                if (rows > 0) new ParkDAO().removeCurrentVisitors(conn, parkId, count);
                return rows > 0;
            }
        } catch (Exception e) { e.printStackTrace(); }
        return false;
    }

    public List<VisitDTO> fetchVisits(String startDate, String endDate) {
        List<VisitDTO> visits = new ArrayList<>();
        String sql = "SELECT visit_id, COALESCE(reservation_id,0) reservation_id, park_id, entry_time, exit_time, visitors_count, TIMESTAMPDIFF(MINUTE, entry_time, COALESCE(exit_time,NOW())) duration FROM visits WHERE DATE(entry_time) BETWEEN ? AND ? ORDER BY entry_time";
        try (Connection conn = DBConnection.getConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, startDate); stmt.setString(2, endDate);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) visits.add(new VisitDTO(rs.getInt("visit_id"), rs.getInt("reservation_id"), rs.getInt("park_id"), rs.getString("entry_time"), rs.getString("exit_time"), rs.getInt("visitors_count"), rs.getLong("duration")));
        } catch (Exception e) { e.printStackTrace(); }
        return visits;
    }
}
