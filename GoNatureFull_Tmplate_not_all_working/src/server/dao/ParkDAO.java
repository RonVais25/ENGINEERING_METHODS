package server.dao;

import common.dto.ParkDTO;
import server.db.DBConnection;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class ParkDAO {
    public List<ParkDTO> findAllParks() {
        List<ParkDTO> parks = new ArrayList<>();
        String sql = "SELECT park_id, park_name, location, maximum_capacity, current_visitors FROM parks ORDER BY park_id";
        try (Connection conn = DBConnection.getConnection(); PreparedStatement stmt = conn.prepareStatement(sql); ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                parks.add(new ParkDTO(rs.getInt("park_id"), rs.getString("park_name"), rs.getString("location"), rs.getInt("maximum_capacity"), rs.getInt("current_visitors")));
            }
        } catch (Exception e) { e.printStackTrace(); }
        return parks;
    }

    public boolean hasCapacity(int parkId, int requestedVisitors) {
        String sql = "SELECT maximum_capacity, current_visitors FROM parks WHERE park_id=?";
        try (Connection conn = DBConnection.getConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, parkId);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) return rs.getInt("current_visitors") + requestedVisitors <= rs.getInt("maximum_capacity");
        } catch (Exception e) { e.printStackTrace(); }
        return false;
    }

    public void addCurrentVisitors(Connection conn, int parkId, int count) throws SQLException {
        try (PreparedStatement stmt = conn.prepareStatement("UPDATE parks SET current_visitors=current_visitors+? WHERE park_id=?")) {
            stmt.setInt(1, count); stmt.setInt(2, parkId); stmt.executeUpdate();
        }
    }

    public void removeCurrentVisitors(Connection conn, int parkId, int count) throws SQLException {
        try (PreparedStatement stmt = conn.prepareStatement("UPDATE parks SET current_visitors=GREATEST(current_visitors-?,0) WHERE park_id=?")) {
            stmt.setInt(1, count); stmt.setInt(2, parkId); stmt.executeUpdate();
        }
    }
}
