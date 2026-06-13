package server.dao;

import common.dto.ReservationDTO;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import server.db.DBConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ReservationDAOMockitoDatabaseTest {

    @Test
    void findByIdShouldReturnReservationWhenRowExists() throws Exception {
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        ResultSet resultSet = mock(ResultSet.class);

        String sql = "SELECT reservation_id, visitor_id, park_id, visit_date, arrival_time, number_of_visitors, visitor_type, status, qr_code FROM reservations WHERE reservation_id=?";
        when(connection.prepareStatement(sql)).thenReturn(statement);
        when(statement.executeQuery()).thenReturn(resultSet);
        when(resultSet.next()).thenReturn(true);

        when(resultSet.getInt("reservation_id")).thenReturn(10);
        when(resultSet.getInt("visitor_id")).thenReturn(1);
        when(resultSet.getInt("park_id")).thenReturn(2);
        when(resultSet.getString("visit_date")).thenReturn("2026-06-20");
        when(resultSet.getString("arrival_time")).thenReturn("10:00:00");
        when(resultSet.getInt("number_of_visitors")).thenReturn(3);
        when(resultSet.getString("visitor_type")).thenReturn("INDIVIDUAL_PREBOOKED");
        when(resultSet.getString("status")).thenReturn("APPROVED");
        when(resultSet.getString("qr_code")).thenReturn("QR-10");

        try (MockedStatic<DBConnection> mockedConnection = mockStatic(DBConnection.class)) {
            mockedConnection.when(DBConnection::getConnection).thenReturn(connection);

            ReservationDTO reservation = new ReservationDAO().findById(10);

            assertNotNull(reservation);
            assertEquals(10, reservation.getReservationId());
            assertEquals("APPROVED", reservation.getStatus());
            assertEquals("QR-10", reservation.getQrCode());
            verify(statement).setInt(1, 10);
        }
    }

    @Test
    void findByIdShouldReturnNullWhenNoRowExists() throws Exception {
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        ResultSet resultSet = mock(ResultSet.class);

        String sql = "SELECT reservation_id, visitor_id, park_id, visit_date, arrival_time, number_of_visitors, visitor_type, status, qr_code FROM reservations WHERE reservation_id=?";
        when(connection.prepareStatement(sql)).thenReturn(statement);
        when(statement.executeQuery()).thenReturn(resultSet);
        when(resultSet.next()).thenReturn(false);

        try (MockedStatic<DBConnection> mockedConnection = mockStatic(DBConnection.class)) {
            mockedConnection.when(DBConnection::getConnection).thenReturn(connection);

            ReservationDTO reservation = new ReservationDAO().findById(999);

            assertNull(reservation);
        }
    }

    @Test
    void updateReservationShouldReturnTrueWhenRowUpdated() throws Exception {
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);

        String sql = "UPDATE reservations SET visit_date=?, number_of_visitors=? WHERE reservation_id=?";
        when(connection.prepareStatement(sql)).thenReturn(statement);
        when(statement.executeUpdate()).thenReturn(1);

        try (MockedStatic<DBConnection> mockedConnection = mockStatic(DBConnection.class)) {
            mockedConnection.when(DBConnection::getConnection).thenReturn(connection);

            boolean result = new ReservationDAO().updateReservation(10, "2026-06-21", 4);

            assertTrue(result);
            verify(statement).setString(1, "2026-06-21");
            verify(statement).setInt(2, 4);
            verify(statement).setInt(3, 10);
        }
    }

    @Test
    void updateStatusShouldUpdateCancelledAtOnlyForCancelledStatus() throws Exception {
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);

        String sql = "UPDATE reservations SET status=?, cancelled_at=CASE WHEN ?='CANCELLED' THEN NOW() ELSE cancelled_at END WHERE reservation_id=?";
        when(connection.prepareStatement(sql)).thenReturn(statement);
        when(statement.executeUpdate()).thenReturn(1);

        try (MockedStatic<DBConnection> mockedConnection = mockStatic(DBConnection.class)) {
            mockedConnection.when(DBConnection::getConnection).thenReturn(connection);

            boolean result = new ReservationDAO().updateStatus(10, "CANCELLED");

            assertTrue(result);
            verify(statement).setString(1, "CANCELLED");
            verify(statement).setString(2, "CANCELLED");
            verify(statement).setInt(3, 10);
        }
    }
}
