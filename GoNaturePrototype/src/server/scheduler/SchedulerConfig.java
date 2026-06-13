package server.scheduler;

import server.db.DotEnv;

/**
 * Timed-job thresholds, read once from the {@code .env} file (via {@link DotEnv})
 * with sensible production defaults baked in. Every value is overridable by an
 * environment key so the same build serves both real operation and the live
 * defense demo.
 *
 * <p><strong>Shrink the {@code .env} values for the live demo.</strong> The real
 * defaults are measured in hours/minutes; a defense cannot wait an hour for a grab
 * window to lapse. Set e.g. {@code GRAB_WINDOW_MINUTES=1} (and a small
 * {@code SCHEDULER_POLL_SECONDS}) in {@code .env} and restart the server so an
 * offered grab expires in a minute and the sweep visibly fires.
 *
 * <p>Recognised keys (with defaults):
 * <ul>
 *   <li>{@code REMINDER_LEAD_HOURS} = 24 — how far ahead the (session b) reminder fires</li>
 *   <li>{@code CONFIRM_TIMEOUT_MINUTES} = 120 — PENDING auto-expiry window (session b)</li>
 *   <li>{@code GRAB_WINDOW_MINUTES} = 60 — how long an offered waiting-list grab stays claimable</li>
 *   <li>{@code SCHEDULER_POLL_SECONDS} = 30 — how often every job re-checks</li>
 * </ul>
 *
 * <p>Values are read at class load (after {@link DotEnv#load()} has run at server
 * startup) and cached, so a {@code .env} edit takes effect on the next restart.
 */
public final class SchedulerConfig {

    private static final int REMINDER_LEAD_HOURS;
    private static final int CONFIRM_TIMEOUT_MINUTES;
    private static final int GRAB_WINDOW_MINUTES;
    private static final int SCHEDULER_POLL_SECONDS;

    static {
        // Idempotent — a no-op if StartServer already loaded the file. Guards the
        // case where this class is touched before the entry point ran load().
        DotEnv.load();
        REMINDER_LEAD_HOURS     = readInt("REMINDER_LEAD_HOURS", 24);
        CONFIRM_TIMEOUT_MINUTES = readInt("CONFIRM_TIMEOUT_MINUTES", 120);
        GRAB_WINDOW_MINUTES     = readInt("GRAB_WINDOW_MINUTES", 60);
        SCHEDULER_POLL_SECONDS  = readInt("SCHEDULER_POLL_SECONDS", 30);
    }

    private SchedulerConfig() {
    }

    /** How far ahead of a visit the confirmation reminder fires, in hours (session b). */
    public static int getReminderLeadHours() {
        return REMINDER_LEAD_HOURS;
    }

    /** How long a PENDING reservation may sit before auto-expiry, in minutes (session b). */
    public static int getConfirmTimeoutMinutes() {
        return CONFIRM_TIMEOUT_MINUTES;
    }

    /** How long an offered waiting-list grab stays claimable, in minutes. */
    public static int getGrabWindowMinutes() {
        return GRAB_WINDOW_MINUTES;
    }

    /** How often the scheduler re-runs every registered job, in seconds. */
    public static int getPollSeconds() {
        return SCHEDULER_POLL_SECONDS;
    }

    /**
     * Reads an integer key from {@link DotEnv}, falling back to {@code def} when the
     * key is absent, blank, or not a valid integer.
     *
     * @param key the {@code .env} key to read
     * @param def the default to use when the key is missing or malformed
     * @return the parsed value, or {@code def}
     */
    private static int readInt(String key, int def) {
        String raw = DotEnv.get(key);
        if (raw == null || raw.trim().isEmpty()) {
            return def;
        }
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            System.out.println("[scheduler] bad value for " + key + "='" + raw + "', using " + def);
            return def;
        }
    }
}
