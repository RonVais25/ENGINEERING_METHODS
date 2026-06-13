package server.dao;

import common.dto.ParkDTO;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import server.db.DBConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ParkDAOMockitoDatabaseTest {

    @Test
    void hasCapacityShouldReturnTrueWhenCurrentPlusRequestedIsWithinMaximum() throws Exception {
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        ResultSet resultSet = mock(ResultSet.class);

        when(connection.prepareStatement("SELECT maximum_capacity, current_visitors FROM parks WHERE park_id=?"))
                .thenReturn(statement);
        when(statement.executeQuery()).thenReturn(resultSet);
        when(resultSet.next()).thenReturn(true);
        when(resultSet.getInt("current_visitors")).thenReturn(7);
        when(resultSet.getInt("maximum_capacity")).thenReturn(10);

        try (MockedStatic<DBConnection> mockedConnection = mockStatic(DBConnection.class)) {
            mockedConnection.when(DBConnection::getConnection).thenReturn(connection);

            boolean result = new ParkDAO().hasCapacity(1, 3);

            assertTrue(result);
            verify(statement).setInt(1, 1);
            verify(statement).executeQuery();
        }
    }

    @Test
    void hasCapacityShouldReturnFalseWhenRequestedExceedsMaximum() throws Exception {
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        ResultSet resultSet = mock(ResultSet.class);

        when(connection.prepareStatement("SELECT maximum_capacity, current_visitors FROM parks WHERE park_id=?"))
                .thenReturn(statement);
        when(statement.executeQuery()).thenReturn(resultSet);
        when(resultSet.next()).thenReturn(true);
        when(resultSet.getInt("current_visitors")).thenReturn(8);
        when(resultSet.getInt("maximum_capacity")).thenReturn(10);

        try (MockedStatic<DBConnection> mockedConnection = mockStatic(DBConnection.class)) {
            mockedConnection.when(DBConnection::getConnection).thenReturn(connection);

            boolean result = new ParkDAO().hasCapacity(1, 3);

            assertFalse(result);
        }
    }

    @Test
    void findAllParksShouldMapResultSetRowsToParkDTOs() throws Exception {
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        ResultSet resultSet = mock(ResultSet.class);

        String sql = "SELECT park_id, park_name, location, maximum_capacity, current_visitors FROM parks ORDER BY park_id";
        when(connection.prepareStatement(sql)).thenReturn(statement);
        when(statement.executeQuery()).thenReturn(resultSet);
        when(resultSet.next()).thenReturn(true, true, false);

        when(resultSet.getInt("park_id")).thenReturn(1, 2);
        when(resultSet.getString("park_name")).thenReturn("Carmel", "Ein Gedi");
        when(resultSet.getString("location")).thenReturn("Haifa", "Dead Sea");
        when(resultSet.getInt("maximum_capacity")).thenReturn(100, 5);
        when(resultSet.getInt("current_visitors")).thenReturn(20, 3);

        try (MockedStatic<DBConnection> mockedConnection = mockStatic(DBConnection.class)) {
            mockedConnection.when(DBConnection::getConnection).thenReturn(connection);

            List<ParkDTO> parks = new ParkDAO().findAllParks();

            assertEquals(2, parks.size());
            assertEquals(1, parks.get(0).getParkId());
            assertEquals("Carmel", parks.get(0).getParkName());
            assertEquals(2, parks.get(1).getParkId());
            assertEquals("Ein Gedi", parks.get(1).getParkName());
        }
    }

    @Test
    void addCurrentVisitorsShouldExecuteUpdateWithCorrectValues() throws Exception {
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);

        when(connection.prepareStatement("UPDATE parks SET current_visitors=current_visitors+? WHERE park_id=?"))
                .thenReturn(statement);

        new ParkDAO().addCurrentVisitors(connection, 2, 3);

        verify(statement).setInt(1, 3);
        verify(statement).setInt(2, 2);
        verify(statement).executeUpdate();
    }

    @Test
    void removeCurrentVisitorsShouldExecuteUpdateWithCorrectValues() throws Exception {
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);

        when(connection.prepareStatement("UPDATE parks SET current_visitors=GREATEST(current_visitors-?,0) WHERE park_id=?"))
                .thenReturn(statement);

        new ParkDAO().removeCurrentVisitors(connection, 2, 3);

        verify(statement).setInt(1, 3);
        verify(statement).setInt(2, 2);
        verify(statement).executeUpdate();
    }
}
