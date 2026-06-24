package common.dto;

import java.io.Serializable;

/**
 * Immutable data transfer object representing a {@code notification} row — a
 * simulated/popup message addressed to exactly one recipient (a visitor or a
 * staff user).
 *
 * <p>Built server-side by {@link server.dao.NotificationDAO} and shipped to the
 * client in two ways:
 * <ul>
 *   <li>as the payload of a realtime {@link ServerEvent} (entity {@code
 *       "notification"}) when a notification is sent to an <em>online</em>
 *       recipient — drives the instant simulation popup; and</li>
 *   <li>inside a {@link ServerResponse} list when the notification center
 *       fetches a recipient's history (the offline-fetch path).</li>
 * </ul>
 *
 * <p>Exactly one of {@link #getRecipientVisitorId()} / {@link #getRecipientUserId()}
 * is non-null. {@link #getSimulatedTarget()} carries the recipient's contact
 * (email or phone) so the simulation popup can render
 * <em>"Simulation — would send via {@code <channel>} to {@code <target>}:
 * {@code <body>}"</em> without a second lookup on the client. For a staff
 * recipient the target is their {@code username}.
 *
 * <p>{@link #getAcknowledgedAt()} backs the notification center's unread
 * highlight: a notification is unread while it is {@code null}. It maps to the
 * {@code notification.acknowledged_at} column added for this feature (see
 * {@code setup.sql}); {@code sent_at} could not double as the seen-marker
 * because it is stamped at delivery time, before the recipient has seen it.
 */
public class NotificationDTO implements Serializable {
    /** Serialization-format version identifier. */
    private static final long serialVersionUID = 1L;

    /** Unique notification identifier (primary key). */
    private final int id;
    /** Recipient visitor's national id, or {@code null} when addressed to a staff user. */
    private final Long recipientVisitorId;
    /** Recipient staff user's id, or {@code null} when addressed to a visitor. */
    private final Integer recipientUserId;
    /** Delivery channel: {@code "SIM_EMAIL"}, {@code "SIM_SMS"}, or {@code "POPUP"}. */
    private final String channel;
    /** Human-readable message body. */
    private final String body;
    /** Recipient contact the message would be sent to (email/phone, or a staff username). */
    private final String simulatedTarget;
    /** Creation timestamp (string form). */
    private final String createdAt;
    /** Delivery timestamp (string form), or {@code null} if not yet delivered. */
    private final String sentAt;
    /** Acknowledgement timestamp (string form), or {@code null} while unread. */
    private final String acknowledgedAt;

    /**
     * Creates a fully populated notification.
     *
     * @param id                 unique notification identifier
     * @param recipientVisitorId recipient visitor's national id, or {@code null} for a staff recipient
     * @param recipientUserId    recipient staff user's id, or {@code null} for a visitor recipient
     * @param channel            delivery channel ({@code SIM_EMAIL}/{@code SIM_SMS}/{@code POPUP})
     * @param body               message body
     * @param simulatedTarget    recipient contact for the simulation popup (email/phone, or username)
     * @param createdAt          creation timestamp (string form)
     * @param sentAt             delivery timestamp (string form), or {@code null}
     * @param acknowledgedAt     acknowledgement timestamp (string form), or {@code null} while unread
     */
    public NotificationDTO(int id, Long recipientVisitorId, Integer recipientUserId,
                           String channel, String body, String simulatedTarget,
                           String createdAt, String sentAt, String acknowledgedAt) {
        this.id = id;
        this.recipientVisitorId = recipientVisitorId;
        this.recipientUserId = recipientUserId;
        this.channel = channel;
        this.body = body;
        this.simulatedTarget = simulatedTarget;
        this.createdAt = createdAt;
        this.sentAt = sentAt;
        this.acknowledgedAt = acknowledgedAt;
    }

    /** {@return the unique notification identifier} */
    public int getId() {
        return id;
    }

    /** {@return the recipient visitor's national id, or {@code null} for a staff recipient} */
    public Long getRecipientVisitorId() {
        return recipientVisitorId;
    }

    /** {@return the recipient staff user's id, or {@code null} for a visitor recipient} */
    public Integer getRecipientUserId() {
        return recipientUserId;
    }

    /** {@return the delivery channel ({@code SIM_EMAIL}/{@code SIM_SMS}/{@code POPUP})} */
    public String getChannel() {
        return channel;
    }

    /** {@return the message body} */
    public String getBody() {
        return body;
    }

    /** {@return the recipient contact the message would be sent to (email/phone, or a staff username)} */
    public String getSimulatedTarget() {
        return simulatedTarget;
    }

    /** {@return the creation timestamp (string form)} */
    public String getCreatedAt() {
        return createdAt;
    }

    /** {@return the delivery timestamp (string form), or {@code null} if not yet delivered} */
    public String getSentAt() {
        return sentAt;
    }

    /** {@return the acknowledgement timestamp (string form), or {@code null} while unread} */
    public String getAcknowledgedAt() {
        return acknowledgedAt;
    }

    /** {@return {@code true} if this notification has been acknowledged (read) by the recipient} */
    public boolean isAcknowledged() {
        return acknowledgedAt != null;
    }
}
