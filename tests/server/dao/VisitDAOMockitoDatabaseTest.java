package server.dao;

import common.dto.VisitDTO;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;
import server.db.DBConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class VisitDAOMockitoDatabaseTest {

    @Test
    void registerEntryShouldInsertVisitUpdateParkVisitorsAndReturnGeneratedVisitId() throws Exception {
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        ResultSet keys = mock(ResultSet.class);

        String sql = "INSERT INTO visits (reservation_id, park_id, entry_time, visitors_count, entry_type) VALUES (?, ?, NOW(), ?, ?)";
        when(connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)).thenReturn(statement);
        when(statement.getGeneratedKeys()).thenReturn(keys);
        when(keys.next()).thenReturn(true);
        when(keys.getInt(1)).thenReturn(500);

        try (MockedStatic<DBConnection> mockedConnection = mockStatic(DBConnection.class);
             MockedConstruction<ParkDAO> parkDaoConstruction = mockConstruction(ParkDAO.class)) {

            mockedConnection.when(DBConnection::getConnection).thenReturn(connection);

            int visitId = new VisitDAO().registerEntry(10, 2, 3, "RESERVATION");

            assertEquals(500, visitId);
            verify(statement).setInt(1, 10);
            verify(statement).setInt(2, 2);
            verify(statement).setInt(3, 3);
            verify(statement).setString(4, "RESERVATION");
            verify(statement).executeUpdate();
            verify(parkDaoConstruction.constructed().get(0)).addCurrentVisitors(connection, 2, 3);
        }
    }

    @Test
    void registerExitShouldUpdateExitTimeAndRemoveCurrentVisitors() throws Exception {
        Connection connection = mock(Connection.class);
        PreparedStatement queryStatement = mock(PreparedStatement.class);
        PreparedStatement updateStatement = mock(PreparedStatement.class);
        ResultSet resultSet = mock(ResultSet.class);

        when(connection.prepareStatement("SELECT park_id, visitors_count FROM visits WHERE reservation_id=? AND exit_time IS NULL ORDER BY visit_id DESC LIMIT 1"))
                .thenReturn(queryStatement);
        when(queryStatement.executeQuery()).thenReturn(resultSet);
        when(resultSet.next()).thenReturn(true);
        when(resultSet.getInt("park_id")).thenReturn(2);
        when(resultSet.getInt("visitors_count")).thenReturn(3);

        when(connection.prepareStatement("UPDATE visits SET exit_time=NOW() WHERE reservation_id=? AND exit_time IS NULL"))
                .thenReturn(updateStatement);
        when(updateStatement.executeUpdate()).thenReturn(1);

        try (MockedStatic<DBConnection> mockedConnection = mockStatic(DBConnection.class);
             MockedConstruction<ParkDAO> parkDaoConstruction = mockConstruction(ParkDAO.class)) {

            mockedConnection.when(DBConnection::getConnection).thenReturn(connection);

            boolean result = new VisitDAO().registerExit(10);

            assertTrue(result);
            verify(queryStatement).setInt(1, 10);
            verify(updateStatement).setInt(1, 10);
            verify(parkDaoConstruction.constructed().get(0)).removeCurrentVisitors(connection, 2, 3);
        }
    }

    @Test
    void registerExitShouldReturnFalseWhenNoOpenVisitExists() throws Exception {
        Connection connection = mock(Connection.class);
        PreparedStatement queryStatement = mock(PreparedStatement.class);
        ResultSet resultSet = mock(ResultSet.class);

        when(connection.prepareStatement("SELECT park_id, visitors_count FROM visits WHERE reservation_id=? AND exit_time IS NULL ORDER BY visit_id DESC LIMIT 1"))
                .thenReturn(queryStatement);
        when(queryStatement.executeQuery()).thenReturn(resultSet);
        when(resultSet.next()).thenReturn(false);

        try (MockedStatic<DBConnection> mockedConnection = mockStatic(DBConnection.class)) {
            mockedConnection.when(DBConnection::getConnection).thenReturn(connection);

            boolean result = new VisitDAO().registerExit(10);

            assertFalse(result);
        }
    }

    @Test
    void fetchVisitsShouldMapRowsToVisitDTOs() throws Exception {
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        ResultSet resultSet = mock(ResultSet.class);

        String sql = "SELECT visit_id, COALESCE(reservation_id,0) reservation_id, park_id, entry_time, exit_time, visitors_count, TIMESTAMPDIFF(MINUTE, entry_time, COALESCE(exit_time,NOW())) duration FROM visits WHERE DATE(entry_time) BETWEEN ? AND ? ORDER BY entry_time";
        when(connection.prepareStatement(sql)).thenReturn(statement);
        when(statement.executeQuery()).thenReturn(resultSet);
        when(resultSet.next()).thenReturn(true, false);

        when(resultSet.getInt("visit_id")).thenReturn(1);
        when(resultSet.getInt("reservation_id")).thenReturn(10);
        when(resultSet.getInt("park_id")).thenReturn(2);
        when(resultSet.getString("entry_time")).thenReturn("2026-06-20 10:00:00");
        when(resultSet.getString("exit_time")).thenReturn("2026-06-20 12:00:00");
        when(resultSet.getInt("visitors_count")).thenReturn(3);
        when(resultSet.getLong("duration")).thenReturn(120L);

        try (MockedStatic<DBConnection> mockedConnection = mockStatic(DBConnection.class)) {
            mockedConnection.when(DBConnection::getConnection).thenReturn(connection);

            List<VisitDTO> visits = new VisitDAO().fetchVisits("2026-01-01", "2026-12-31");

            assertEquals(1, visits.size());
            assertEquals(1, visits.get(0).getVisitId());
            assertEquals(120L, visits.get(0).getStayDurationMinutes());
            verify(statement).setString(1, "2026-01-01");
            verify(statement).setString(2, "2026-12-31");
        }
    }
}
