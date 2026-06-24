package server.dao;

import server.db.DBConnection;
import server.util.ServerLog;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLIntegrityConstraintViolationException;

/**
 * Data access object for member registration: the {@code visitor} base row plus
 * the {@code subscriber} and {@code guide} extension rows that a SERVICE_REP
 * creates.
 *
 * <p>Follows the same conventions as {@link AuthDAO} and {@link ReservationDAO}:
 * each method opens a short-lived {@link java.sql.Connection} from
 * {@link server.db.DBConnection}, runs parameterized statements, and signals
 * failure through the return value rather than propagating SQL exceptions. A
 * subscriber or guide <em>is</em> a visitor with one extra row, so callers
 * first {@link #upsertVisitor} the base identity and then add the role row.
 *
 * <p>Duplicate detection (already a subscriber / already a guide) relies on the
 * extension tables' {@code visitor_id} primary key: the role-row insert is
 * attempted and a {@link SQLIntegrityConstraintViolationException} is caught and
 * reported as {@code false}. This mirrors {@link AuthDAO#lock} and avoids the
 * check-then-insert race a separate existence query would open.
 */
public class MemberDAO {

    /** Creates the member DAO. */
    public MemberDAO() { }

    /**
     * Checks whether a visitor row already exists for the given national ID.
     *
     * @param id the visitor (national) id to probe
     * @return {@code true} if a matching {@code visitor} row exists, {@code false}
     *         if none matches or the query fails
     */
    public boolean visitorExists(long id) {
        String sql = "SELECT 1 FROM visitor WHERE id = ?";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setLong(1, id);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next();
            }

        } catch (Exception e) {
            ServerLog.daoError(e);
        }

        return false;
    }

    /**
     * Find-or-create the base {@code visitor} row for a national ID.
     *
     * <p>{@code visitor.id} is an assigned national ID (not auto-increment), so a
     * new visitor is inserted with the id supplied from the form; re-registering
     * an existing visitor updates their details instead of failing on the primary
     * key. The {@code is_subscriber} flag is only ever raised, never lowered: when
     * registering a subscriber ({@code isSubscriber == true}) it is set
     * {@code TRUE}, but registering an existing subscriber as a guide
     * ({@code isSubscriber == false}) leaves their subscription flag untouched so
     * the member discount keeps firing.
     *
     * <p>Password handling: a brand-new visitor is seeded with the shared demo
     * password {@code 'changeme'} so a newly created subscriber/guide can log in
     * (visitor login is national ID + password). An existing visitor's
     * {@code password_hash} is never overwritten here — and, on the subscriber
     * upgrade path, neither is their {@code full_name} — so a visitor who already
     * self-registered keeps the login and name they chose; only their contact
     * details (and the raised subscriber flag) change.
     *
     * @param id           the visitor's assigned national id (primary key)
     * @param fullName     the visitor's display name (used only when inserting a new
     *                     visitor or on the guide path; ignored on a subscriber upgrade)
     * @param phone        the visitor's phone number
     * @param email        the visitor's email address
     * @param isSubscriber whether to mark the visitor as a subscriber; {@code false}
     *                     never clears an existing subscription
     */
    public void upsertVisitor(long id, String fullName, String phone, String email, boolean isSubscriber) {
        if (visitorExists(id)) {
            if (isSubscriber) {
                // Subscriber upgrade of an existing (self-)registered visitor: refresh
                // contact and raise the flag, but leave full_name and password_hash
                // untouched so the visitor's chosen name and login survive the upgrade.
                String sql = "UPDATE visitor SET phone = ?, email = ?, is_subscriber = TRUE WHERE id = ?";

                try (Connection conn = DBConnection.getConnection();
                     PreparedStatement stmt = conn.prepareStatement(sql)) {

                    stmt.setString(1, phone);
                    stmt.setString(2, email);
                    stmt.setLong(3, id);
                    stmt.executeUpdate();

                } catch (Exception e) {
                    ServerLog.daoError(e);
                }
            } else {
                // Guide path: refresh contact + name without touching is_subscriber
                // (must not downgrade an existing subscriber) or password_hash.
                String sql = "UPDATE visitor SET full_name = ?, phone = ?, email = ? WHERE id = ?";

                try (Connection conn = DBConnection.getConnection();
                     PreparedStatement stmt = conn.prepareStatement(sql)) {

                    stmt.setString(1, fullName);
                    stmt.setString(2, phone);
                    stmt.setString(3, email);
                    stmt.setLong(4, id);
                    stmt.executeUpdate();

                } catch (Exception e) {
                    ServerLog.daoError(e);
                }
            }
        } else {
            // Brand-new visitor: seed the shared demo password 'changeme' so a newly
            // created subscriber/guide can sign in (visitor login is id + password).
            String sql = "INSERT INTO visitor (id, full_name, phone, email, is_subscriber, password_hash) " +
                         "VALUES (?, ?, ?, ?, ?, 'changeme')";

            try (Connection conn = DBConnection.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setLong(1, id);
                stmt.setString(2, fullName);
                stmt.setString(3, phone);
                stmt.setString(4, email);
                stmt.setBoolean(5, isSubscriber);
                stmt.executeUpdate();

            } catch (Exception e) {
                ServerLog.daoError(e);
            }
        }
    }

    /**
     * Find-or-create a visitor by national ID, saving only their contact details.
     *
     * <p>Used by the booking path so a reservation's {@code visitor_id} foreign key
     * always resolves and the visitor's email/phone are on file for notifications.
     * Unlike {@link #upsertVisitor}, this never writes {@code full_name} or
     * {@code is_subscriber}: a brand-new visitor is inserted with those left at
     * their defaults ({@code full_name} NULL, {@code is_subscriber} FALSE), while an
     * existing visitor has <em>only</em> their two contact columns updated. That
     * matters when a registered subscriber re-books — their name and subscription
     * flag (and thus the member discount) must survive the upsert untouched.
     *
     * @param id    the visitor's assigned national id (primary key)
     * @param email the visitor's email address
     * @param phone the visitor's phone number
     */
    public void upsertContact(long id, String email, String phone) {
        // Update only the two contact columns on an existing row so a subscriber's
        // full_name / is_subscriber / password are never clobbered; insert a
        // contact-only row (name NULL, not a subscriber) when the visitor is brand
        // new. The insert seeds the shared demo password 'changeme' because
        // password_hash is NOT NULL — a booking-created visitor can then log in too.
        boolean exists = visitorExists(id);
        String sql = exists
                ? "UPDATE visitor SET email = ?, phone = ? WHERE id = ?"
                : "INSERT INTO visitor (id, email, phone, password_hash) VALUES (?, ?, ?, 'changeme')";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            if (exists) {
                stmt.setString(1, email);
                stmt.setString(2, phone);
                stmt.setLong(3, id);
            } else {
                stmt.setLong(1, id);
                stmt.setString(2, email);
                stmt.setString(3, phone);
            }
            stmt.executeUpdate();

        } catch (Exception e) {
            ServerLog.daoError(e);
        }
    }

    /**
     * Promotes a visitor to a subscriber: marks {@code visitor.is_subscriber}
     * {@code TRUE} and inserts the {@code subscriber} row with today's join date.
     *
     * <p>The caller must have created the base visitor (see {@link #upsertVisitor})
     * first. The {@code subscriber} insert is attempted directly; a duplicate
     * primary key means the visitor is already a subscriber, which is caught and
     * reported as {@code false} rather than raising an error.
     *
     * <p>The fake/demo {@code creditCard} is stored on the subscriber row as it is
     * created, so both paths into this method — a brand-new subscriber and the
     * upgrade of an existing registered visitor — capture the card. There is no
     * real payment processing; the value is validated for shape by the caller.
     *
     * @param visitorId  the visitor's national id
     * @param familySize the subscriber's family size
     * @param creditCard the subscriber's (fake/demo) credit-card number, as entered
     * @return {@code true} if a new subscriber row was created, {@code false} if
     *         the visitor was already a subscriber or the insert failed
     */
    public boolean registerSubscriber(long visitorId, int familySize, String creditCard) {
        String insertSql = "INSERT INTO subscriber (visitor_id, family_size, joined_on, credit_card) "
                         + "VALUES (?, ?, CURDATE(), ?)";
        String flagSql   = "UPDATE visitor SET is_subscriber = TRUE WHERE id = ?";

        try (Connection conn = DBConnection.getConnection()) {

            try (PreparedStatement insert = conn.prepareStatement(insertSql)) {
                insert.setLong(1, visitorId);
                insert.setInt(2, familySize);
                insert.setString(3, creditCard);
                insert.executeUpdate();
            } catch (SQLIntegrityConstraintViolationException dup) {
                // Duplicate subscriber.visitor_id -> already a subscriber.
                return false;
            }

            // New subscriber row created: make sure the visitor flag is set so the
            // member discount in PricingService fires for their bookings.
            try (PreparedStatement flag = conn.prepareStatement(flagSql)) {
                flag.setLong(1, visitorId);
                flag.executeUpdate();
            }
            return true;

        } catch (Exception e) {
            ServerLog.daoError(e);
            return false;
        }
    }

    /**
     * Registers a visitor as a group guide by inserting the {@code guide} row with
     * today's approval date and the service rep who registered them.
     *
     * <p>The caller must have created the base visitor (see {@link #upsertVisitor})
     * first. The insert is attempted directly; a duplicate primary key means the
     * visitor is already a guide, which is caught and reported as {@code false}.
     *
     * @param visitorId          the visitor's national id
     * @param registeredByUserId the id of the SERVICE_REP performing the registration
     * @return {@code true} if a new guide row was created, {@code false} if the
     *         visitor was already a guide or the insert failed
     */
    public boolean registerGuide(long visitorId, int registeredByUserId) {
        String sql = "INSERT INTO guide (visitor_id, registered_by, approved_on) VALUES (?, ?, CURDATE())";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setLong(1, visitorId);
            stmt.setInt(2, registeredByUserId);
            stmt.executeUpdate();
            return true;

        } catch (SQLIntegrityConstraintViolationException dup) {
            // Duplicate guide.visitor_id -> already a registered guide.
            return false;
        } catch (Exception e) {
            ServerLog.daoError(e);
            return false;
        }
    }
}
