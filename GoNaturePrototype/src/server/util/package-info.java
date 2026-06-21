/**
 * Server-side utilities. Currently {@link server.util.ServerLog}: a minimal,
 * dependency-free wrapper over {@code System.err} that logs failures (especially
 * from the DAOs) as a located line plus stack trace, so errors surface in the
 * console instead of being swallowed.
 */
package server.util;
