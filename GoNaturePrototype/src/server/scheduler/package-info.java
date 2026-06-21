/**
 * Server-side timed jobs: reminders, confirmation timeouts, waiting-list grab
 * expiry, and no-show marking. {@code SchedulerService} runs each
 * {@code SchedulerJob} on the cadence set by {@code SchedulerConfig}, driving the
 * reservation lifecycle without any user action.
 */
package server.scheduler;
