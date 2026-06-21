/**
 * Serializable data transfer objects and enums shared by client and server.
 * These are the wire contract exchanged over the socket: request/response
 * envelopes ({@code ClientRequest}, {@code ServerResponse}, {@code ServerEvent}),
 * domain records (reservations, visits, parks, users, subscribers, waiting-list
 * entries, notifications, reports), and the enums that classify them
 * ({@code RequestType}, {@code Role}, {@code ReservationStatus}, …).
 */
package common.dto;
