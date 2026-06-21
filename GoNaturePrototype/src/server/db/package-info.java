/**
 * Database access plumbing: {@link server.db.DBConnection} manages the JDBC
 * connection to MySQL, and {@link server.db.DotEnv} loads connection credentials
 * from a {@code .env} file into system properties so they stay out of source.
 */
package server.db;
