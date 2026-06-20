package server.util;

/**
 * Minimal server-side error logging — a thin, consistent wrapper over
 * {@code System.err} so a failure always shows up in the server console as one
 * located line plus its stack trace, rather than vanishing.
 *
 * <p>Deliberately <em>not</em> a logging framework: the project carries no
 * logging dependency and the server already routes activity through
 * {@link server.net.ServerListener}. This helper covers the two places that
 * cannot reach a listener — the {@code server.dao} DAOs (no listener handle)
 * and any other server-side {@code catch} that would otherwise swallow a
 * throwable. The per-request guard in {@code OrderServer} pairs a
 * {@link #error(String, Throwable)} call (full stack to the console) with a
 * {@code listener.onError(...)} line (the red entry in the GUI activity log).
 *
 * <p>Both methods write to {@code System.err} — the same stream
 * {@link Throwable#printStackTrace()} uses — so the located line and its trace
 * stay together in the console.
 */
public final class ServerLog {

    private ServerLog() {}

    /**
     * Logs a located error: prints {@code "<context>: <message>"} followed by
     * the full stack trace to {@code System.err}.
     *
     * @param context human-readable location/operation (e.g. an op + session,
     *                or a {@code [DAO] Class.method failed} tag)
     * @param t       the failure to report
     */
    public static void error(String context, Throwable t) {
        System.err.println(context + ": " + message(t));
        t.printStackTrace();
    }

    /**
     * Logs a DAO failure as
     * {@code [DAO] <Class>.<method> failed: <message>} followed by the stack
     * trace. The class and method are derived from the calling frame so the line
     * stays correct even if the method is later renamed. Call it directly from a
     * DAO {@code catch} block: {@code ServerLog.daoError(e);}
     *
     * @param t the SQL (or other) failure caught in the DAO
     */
    public static void daoError(Throwable t) {
        StackTraceElement caller = callerFrame();
        String where = (caller == null)
                ? "?"
                : simpleName(caller.getClassName()) + "." + caller.getMethodName();
        error("[DAO] " + where + " failed", t);
    }

    /**
     * The frame that called {@link #daoError(Throwable)}. Index 0 is this
     * method, 1 is {@code daoError}, so 2 is the DAO method that caught the
     * exception.
     */
    private static StackTraceElement callerFrame() {
        StackTraceElement[] frames = new Throwable().getStackTrace();
        return frames.length > 2 ? frames[2] : null;
    }

    /** Strips the package prefix from a fully-qualified class name. */
    private static String simpleName(String className) {
        int dot = className.lastIndexOf('.');
        return dot < 0 ? className : className.substring(dot + 1);
    }

    /** A non-null message for {@code t}, falling back to its type when blank. */
    private static String message(Throwable t) {
        String m = t.getMessage();
        return (m == null || m.isBlank()) ? t.getClass().getSimpleName() : m;
    }
}
