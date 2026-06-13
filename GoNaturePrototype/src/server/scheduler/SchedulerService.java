package server.scheduler;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * Runs the registered {@link SchedulerJob}s on a shared
 * {@link ScheduledExecutorService}, polling every
 * {@link SchedulerConfig#getPollSeconds()} seconds. Owned by
 * {@link server.net.OrderServer}: {@link #start()} is called on server startup and
 * {@link #shutdown()} on server stop, so the threads live exactly as long as the
 * server and never leak.
 *
 * <p><strong>Exception isolation.</strong> Every job run goes through
 * {@link #safeRun}, which swallows and logs any exception. A
 * {@code scheduleAtFixedRate} task that throws is silently cancelled by the JDK
 * and would never run again; containing the throw keeps every job alive across a
 * one-off failure (a transient DB blip, say).
 *
 * <p><strong>Manual triggers.</strong> {@link #runNow(String)} and
 * {@link #runAllNow()} submit an off-cycle run to the same executor — the server
 * console wires its "run now" buttons to these for the live defense.
 */
public class SchedulerService {

    /** Insertion-ordered so the console lists jobs in a stable order. */
    private final Map<String, SchedulerJob> jobs = new LinkedHashMap<>();

    /** One-line summary sink, forwarded to each job and used for lifecycle logging. */
    private final Consumer<String> log;

    /** How often each job re-runs, captured once from {@link SchedulerConfig}. */
    private final long pollSeconds;

    private final ScheduledExecutorService executor;

    private volatile boolean started;

    /**
     * Builds the service and registers the jobs this session ships. The jobs share
     * the supplied {@code log} sink so their one-line summaries land in the server
     * console's activity log.
     *
     * @param log sink for scheduler/job summary lines (e.g. {@code listener::onLog})
     */
    public SchedulerService(Consumer<String> log) {
        this.log = log;
        this.pollSeconds = SchedulerConfig.getPollSeconds();
        this.executor = Executors.newScheduledThreadPool(2, daemonThreads());

        register(new WaitlistGrabExpiryJob(log));
        register(new NoShowJob(log));
        register(new ReminderJob(log));
        register(new ConfirmTimeoutJob(log));
    }

    private void register(SchedulerJob job) {
        jobs.put(job.name(), job);
    }

    /**
     * Schedules every registered job at the configured fixed interval. Idempotent —
     * a second call is ignored.
     */
    public void start() {
        if (started) {
            return;
        }
        started = true;
        for (SchedulerJob job : jobs.values()) {
            executor.scheduleAtFixedRate(() -> safeRun(job), pollSeconds, pollSeconds, TimeUnit.SECONDS);
        }
        log.accept("[scheduler] started — " + jobs.size() + " job(s), polling every "
                + pollSeconds + "s");
    }

    /**
     * Triggers one off-cycle run of the named job, off the caller's thread (so an
     * FX button handler does not block). A no-op with a log line if the name is
     * unknown.
     *
     * @param jobName the job's {@link SchedulerJob#name()}
     */
    public void runNow(String jobName) {
        SchedulerJob job = jobs.get(jobName);
        if (job == null) {
            log.accept("[scheduler] run-now: no job named '" + jobName + "'");
            return;
        }
        executor.submit(() -> {
            log.accept("[scheduler] run-now: " + jobName);
            safeRun(job);
        });
    }

    /** Triggers an off-cycle run of every registered job. */
    public void runAllNow() {
        for (String name : jobs.keySet()) {
            runNow(name);
        }
    }

    /**
     * The names of the registered jobs, in registration order — used by the server
     * console to build one trigger control per job.
     *
     * @return the job names
     */
    public Set<String> jobNames() {
        return jobs.keySet();
    }

    /**
     * Runs a job, containing any exception so the executor (and the job's recurring
     * schedule) survives a one-off failure.
     */
    private void safeRun(SchedulerJob job) {
        try {
            job.runOnce();
        } catch (Exception e) {
            log.accept("[scheduler] job '" + job.name() + "' failed: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Stops polling and shuts the executor down, waiting briefly for in-flight runs
     * to finish. Called from {@link server.net.OrderServer#stop()} so no scheduler
     * threads outlive the server.
     */
    public void shutdown() {
        executor.shutdownNow();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                log.accept("[scheduler] executor did not terminate within 5s");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        log.accept("[scheduler] stopped");
    }

    /**
     * Names the threads and marks them daemon so a missed {@link #shutdown()} can
     * never keep the JVM alive.
     */
    private static ThreadFactory daemonThreads() {
        AtomicInteger n = new AtomicInteger(1);
        return r -> {
            Thread t = new Thread(r, "Scheduler-" + n.getAndIncrement());
            t.setDaemon(true);
            return t;
        };
    }
}
