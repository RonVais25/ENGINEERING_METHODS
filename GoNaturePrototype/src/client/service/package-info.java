/**
 * Client service layer: a typed facade over the raw connection. Controllers call
 * {@link client.service.NetworkService} methods (login, reservations, reports,
 * occupancy, …) instead of building {@code ClientRequest}s by hand, keeping the
 * wire protocol out of the views.
 */
package client.service;
