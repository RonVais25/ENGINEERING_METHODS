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
     */
    void runOnce();
}
