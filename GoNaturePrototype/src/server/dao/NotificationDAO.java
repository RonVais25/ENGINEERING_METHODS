package server.dao;

import common.dto.NotificationDTO;
import server.db.DBConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * Data access object for the {@code notification} table.
 *
 * <p>Follows the same conventions as {@link OrderDAO}: each method opens a
 * short-lived {@link java.sql.Connection} from {@link server.db.DBConnection},
 * runs parameterized statements, and maps rows to {@link NotificationDTO}. SQL
 * exceptions are logged and surfaced as a {@code null}/{@code false}/{@code -1}
 * return rather than being propagated, so callers signal failure through the
 * return value.
 *
 * <p>The read queries all build on {@link #SELECT_BASE}, which {@code LEFT JOIN}s
 * both {@code visitor} and {@code user} so {@link #map} can resolve the
 * recipient's contact into {@link NotificationDTO#getSimulatedTarget()} (email or
 * phone for a visitor, depending on the channel; {@code username} for a staff
 * user, since the {@code user} table has no email/phone column).
 */
public class NotificationDAO {

    /**
     * Shared SELECT prefix for every read. Joins the recipient's contact columns
     * so {@link #map} can compute the simulated target in one round-trip. Callers
     * append a {@code WHERE}/{@code ORDER BY} clause.
     */
    private static final String SELECT_BASE =
            "SELECT n.id, n.recipient_visitor_id, n.recipient_user_id, n.channel, n.body, " +
            "       n.created_at, n.sent_at, n.acknowledged_at, " +
            "       v.email AS v_email, v.phone AS v_phone, u.username AS u_username " +
            "FROM notification n " +
            "LEFT JOIN visitor v ON v.id = n.recipient_visitor_id " +
            "LEFT JOIN `user`  u ON u.id = n.recipient_user_id ";

    /**
     * Inserts a new notification, letting the database assign the auto-increment
     * id and stamp {@code created_at}. {@code sent_at}/{@code acknowledged_at} are
     * left NULL — call {@link #markSent(int)} once delivered. Exactly one of
     * {@code recipientVisitorId}/{@code recipientUserId} should be non-null.
     *
     * @param recipientVisitorId the recipient visitor's national id, or {@code null} for a staff recipient
     * @param recipientUserId    the recipient staff user's id, or {@code null} for a visitor recipient
     * @param channel            delivery channel ({@code SIM_EMAIL}/{@code SIM_SMS}/{@code POPUP})
     * @param body               the message body
     * @return the new auto-generated id, or {@code -1} if the insert fails
     */
    public int insert(Long recipientVisitorId, Integer recipientUserId, String channel, String body) {
        String sql = "INSERT INTO notification " +
                "(recipient_visitor_id, recipient_user_id, channel, body) VALUES (?, ?, ?, ?)";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            if (recipientVisitorId != null) {
                stmt.setLong(1, recipientVisitorId);
            } else {
                stmt.setNull(1, java.sql.Types.BIGINT);
            }
            if (recipientUserId != null) {
                stmt.setInt(2, recipientUserId);
            } else {
                stmt.setNull(2, java.sql.Types.INTEGER);
            }
            stmt.setString(3, channel);
            stmt.setString(4, body);

            if (stmt.executeUpdate() == 1) {
                try (ResultSet keys = stmt.getGeneratedKeys()) {
                    if (keys.next()) {
                        return keys.getInt(1);
                    }
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        return -1;
    }

    /**
     * Marks a notification as delivered by stamping {@code sent_at = NOW()}.
     *
     * @param id the notification to mark
     * @return {@code true} if a row was updated, {@code false} otherwise
     */
    public boolean markSent(int id) {
        String sql = "UPDATE notification SET sent_at = NOW() WHERE id = ?";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, id);
            return stmt.executeUpdate() > 0;

        } catch (Exception e) {
            e.printStackTrace();
        }

        return false;
    }

    /**
     * Acknowledges a notification (marks it read) by stamping
     * {@code acknowledged_at = NOW()} — the seen-marker behind the notification
     * center's unread highlight. Idempotent at the application level; re-acking
     * simply refreshes the timestamp.
     *
     * @param id the notification to acknowledge
     * @return {@code true} if a row was updated, {@code false} otherwise
     */
    public boolean ack(int id) {
        String sql = "UPDATE notification SET acknowledged_at = NOW() WHERE id = ?";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, id);
            return stmt.executeUpdate() > 0;

        } catch (Exception e) {
            e.printStackTrace();
        }

        return false;
    }

    /**
     * Looks up a single notification by its primary key, with the recipient's
     * contact joined in for {@link NotificationDTO#getSimulatedTarget()}. Used by
     * {@link server.control.NotificationService} to build the event payload right
     * after sending.
     *
     * @param id the notification identifier to fetch
     * @return the matching {@link NotificationDTO}, or {@code null} if none matches or the query fails
     */
    public NotificationDTO getById(int id) {
        String sql = SELECT_BASE + "WHERE n.id = ?";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, id);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return map(rs);
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        return null;
    }

    /**
     * Lists every notification addressed to a visitor, newest first.
     *
     * @param visitorId the recipient visitor's national id
     * @return the visitor's notifications (possibly empty); never {@code null}
     */
    public List<NotificationDTO> listForVisitor(long visitorId) {
        String sql = SELECT_BASE + "WHERE n.recipient_visitor_id = ? ORDER BY n.id DESC";
        return query(sql, visitorId);
    }

    /**
     * Lists every notification addressed to a staff user, newest first.
     *
     * @param userId the recipient staff user's id
     * @return the user's notifications (possibly empty); never {@code null}
     */
    public List<NotificationDTO> listForUser(int userId) {
        String sql = SELECT_BASE + "WHERE n.recipient_user_id = ? ORDER BY n.id DESC";
        return query(sql, userId);
    }

    /**
     * Runs one of the recipient-filtered list queries with a single bound
     * recipient id and maps every row.
     *
     * @param sql        the {@link #SELECT_BASE}-derived query with one {@code ?} parameter
     * @param recipientId the visitor or user id to bind
     * @return the mapped notifications (possibly empty); never {@code null}
     */
    private List<NotificationDTO> query(String sql, long recipientId) {
        List<NotificationDTO> result = new ArrayList<>();

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setLong(1, recipientId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    result.add(map(rs));
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        return result;
    }

    /**
     * Maps the current row of a {@link ResultSet} (selected via {@link #SELECT_BASE})
     * to a {@link NotificationDTO}, resolving the recipient's contact into the
     * simulated target.
     *
     * <p>For a visitor recipient the target is their phone when the channel is
     * {@code SIM_SMS}, otherwise their email. For a staff recipient the target is
     * their {@code username}, since the {@code user} table carries no email/phone.
     * The nullable {@code recipient_*} columns use {@code wasNull()} so a SQL
     * {@code NULL} becomes a Java {@code null} instead of a zero.
     *
     * @param rs a result set positioned on a notification row
     * @return the mapped notification
     * @throws java.sql.SQLException if a column cannot be read
     */
    private NotificationDTO map(ResultSet rs) throws java.sql.SQLException {
        long visitorRaw = rs.getLong("recipient_visitor_id");
        Long recipientVisitorId = rs.wasNull() ? null : visitorRaw;

        int userRaw = rs.getInt("recipient_user_id");
        Integer recipientUserId = rs.wasNull() ? null : userRaw;

        String channel = rs.getString("channel");

        String simulatedTarget;
        if (recipientVisitorId != null) {
            simulatedTarget = "SIM_SMS".equals(channel)
                    ? rs.getString("v_phone")
                    : rs.getString("v_email");
        } else {
            simulatedTarget = rs.getString("u_username");
        }

        return new NotificationDTO(
                rs.getInt("id"),
                recipientVisitorId,
                recipientUserId,
                channel,
                rs.getString("body"),
                simulatedTarget,
                rs.getString("created_at"),
                rs.getString("sent_at"),
                rs.getString("acknowledged_at")
        );
    }
}
