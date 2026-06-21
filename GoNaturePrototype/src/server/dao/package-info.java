/**
 * Data access layer: one DAO per aggregate (auth, members, parks, reservations,
 * visits, waiting list, notifications, reports, parameter-change requests). DAOs
 * own all JDBC — prepared statements, transactions, and row-to-DTO mapping — and
 * are the only classes that talk to MySQL via {@link server.db.DBConnection}.
 */
package server.dao;
