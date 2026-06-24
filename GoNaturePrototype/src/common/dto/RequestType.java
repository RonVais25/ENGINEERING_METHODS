package common.dto;
/**
 * Defines all request types that can be sent between
 * client and server.
 */
public enum RequestType {
    // infrastructure
    /** Connectivity probe; the server replies with a success response. */
    PING,
    /** Subscribe to a real-time push channel (e.g. a park's occupancy). */
    SUBSCRIBE,
    /** Cancel a previous {@link #SUBSCRIBE}. */
    UNSUBSCRIBE,

    // auth
    /** Authenticate a staff user by username and password. */
    LOGIN_STAFF,
    /** Authenticate a visitor by national id and password. */
    LOGIN_VISITOR,
    /** Log the current actor out and release the single-login lock. */
    LOGOUT,
    /** Self-service signup: create a regular (non-subscriber) visitor account. */
    REGISTER_VISITOR,
    /** Register a visitor as a subscriber (service rep only). */
    REGISTER_SUBSCRIBER,
    /** Register a visitor as a group guide (service rep only). */
    REGISTER_GUIDE,
    /** Edit the logged-in actor's own personal details (name / email / phone). */
    UPDATE_PROFILE,

    // reservations
    /** Create a new reservation. */
    CREATE_RESERVATION,
    /** Fetch a single reservation by id. */
    GET_RESERVATION,
    /** Reschedule a reservation (date / time / party size). */
    UPDATE_RESERVATION,
    /** Cancel a reservation. */
    CANCEL_RESERVATION,
    /** List all reservations owned by a visitor. */
    LIST_RESERVATIONS,
    /** Confirm a pending reservation. */
    CONFIRM_RESERVATION,
    /** Join the waiting list for a full park/date. */
    JOIN_WAITLIST,
    /** Leave the waiting list (or decline an active grab offer). */
    LEAVE_WAITLIST,
    /** Accept an active waiting-list grab offer. */
    ACCEPT_GRAB,
    /** List a visitor's active waiting-list entries. */
    LIST_WAITLIST,

    // parks
    /** Fetch the logged-in park manager's own park. */
    GET_PARK,
    /** List every park (e.g. for the booking dropdown). */
    LIST_PARKS,
    /** Submit a park-parameter change request (park manager only). */
    REQUEST_PARAM_CHANGE,
    /** List all pending parameter-change requests (dept. manager only). */
    LIST_PENDING_CHANGES,
    /** Approve a pending parameter-change request (dept. manager only). */
    APPROVE_PARAM_CHANGE,
    /** Reject a pending parameter-change request (dept. manager only). */
    REJECT_PARAM_CHANGE,
    /** Check booking availability for a park/date. */
    CHECK_AVAILABILITY,

    // promotions
    /** Define a temporary park promotion for approval (park manager only). */
    CREATE_PROMOTION,
    /** List the park manager's own promotions, all statuses (park manager only). */
    LIST_PROMOTIONS,
    /** List all pending promotions across parks (dept. manager only). */
    LIST_PENDING_PROMOTIONS,
    /** Approve a pending promotion (dept. manager only). */
    APPROVE_PROMOTION,
    /** Reject a pending promotion (dept. manager only). */
    REJECT_PROMOTION,

    // visits
    /** Admit a visitor against a confirmed reservation at the gate. */
    ENTER_VISIT,
    /** Record a visitor's exit, closing their open visit. */
    EXIT_VISIT,
    /** Record a casual walk-in visit at the gate. */
    CASUAL_VISIT,
    /** Fetch live occupancy for the employee's own park. */
    CURRENT_OCCUPANCY,

    // reports
    /** Run the Visits-by-Type report (dept. manager only). */
    REPORT_VISITS_BY_TYPE,
    /** Run the Cancellations report (dept. manager only). */
    REPORT_CANCELLATIONS,
    /** Run the Usage report for the manager's own park (park manager only). */
    REPORT_USAGE,
    /** Run the Total-Visitors-by-Type report for the manager's own park (park manager only). */
    REPORT_TOTAL_VISITORS,

    // notifications
    /** List the logged-in actor's notifications, newest first. */
    LIST_NOTIFICATIONS,
    /** Acknowledge (mark read) a single notification. */
    ACK_NOTIFICATION
}
