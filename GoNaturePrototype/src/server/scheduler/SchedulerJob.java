package server.scheduler;

/**
 * One timed maintenance task driven by {@link SchedulerService}. A job is plain,
 * synchronous, idempotent-by-intent code: the service handles the threading,
 * the fixed-interval polling, exception isolation, and manual "run now" triggers,
 * so an implementation only describes <em>what</em> to do, not <em>when</em>.
 *
 * <p>A job must catch nothing for control flow — uncaught exceptions are contained
 * by the service so one failing run never kills the shared executor — and should
 * log a one-line summary only when it actually acts, keeping the server console
 * quiet on idle polls.
 *
 * <p><strong>Force mode.</strong> Every job runs in one of two modes. The
 * scheduled timer calls {@link #runOnce(boolean) runOnce(false)} — the normal
 * sweep, which only touches rows past the job's real time threshold (24h/2h/1h).
 * A manual "run now" from the server console calls {@code runOnce(true)}, which
 * drops the time predicate so the job acts on every eligible row immediately —
 * letting the live defense produce each effect on demand without shrinking
 * {@code .env} or waiting for a window to lapse.
 */
public interface SchedulerJob {

    /**
     * Stable identifier for this job, used as the registry key and the handle the
     * server console passes to {@link SchedulerService#runNow(String)}.
     *
     * @return the job's unique name
     */
    String name();

    /**
     * Performs one sweep. Invoked on a scheduler thread, every poll interval and on
     * each manual trigger.
     *
     * @param force when {@code true}, drop the job's time threshold and act on
     *              every eligible row now (the manual "run now" path); when
     *              {@code false}, the normal scheduled sweep that respects the
     *              real 24h/2h/1h window
     */
    void runOnce(boolean force);

    /**
     * Convenience for the normal, non-forced sweep — equivalent to
     * {@link #runOnce(boolean) runOnce(false)}.
     */
    default void runOnce() {
        runOnce(false);
    }
}
