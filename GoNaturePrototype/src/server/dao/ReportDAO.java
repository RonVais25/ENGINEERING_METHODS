package server.dao;

import server.db.DBConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

public class ReportDAO {

    public void generateVisitsReport() {
        String sql =
                "SELECT " +
                "v.reservation_id, " +
                "p.park_name, " +
                "v.visitors_count, " +
                "v.entry_time, " +
                "v.exit_time, " +
                "TIMESTAMPDIFF(SECOND, v.entry_time, v.exit_time) AS stay_seconds " +
                "FROM visits v " +
                "JOIN parks p ON v.park_id = p.park_id " +
                "ORDER BY v.entry_time DESC";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {

            System.out.println("\n===== VISITS REPORT FROM DB =====");

            while (rs.next()) {
                System.out.println("--------------------------------");
                System.out.println("Reservation ID: " + rs.getString("reservation_id"));
                System.out.println("Park: " + rs.getString("park_name"));
                System.out.println("Visitors: " + rs.getInt("visitors_count"));
                System.out.println("Entry Time: " + rs.getTimestamp("entry_time"));
                System.out.println("Exit Time: " + rs.getTimestamp("exit_time"));
                System.out.println("Stay Duration Seconds: " + rs.getInt("stay_seconds"));
            }

            System.out.println("--------------------------------");

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}