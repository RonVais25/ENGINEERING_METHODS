/**
 * Server business logic: the controllers and services that implement GoNature's
 * use cases. {@code DomainController} dispatches each request to the right
 * handler — authentication, reservations, visits, parks, reports, notifications
 * — while {@code PricingService} and {@code NotificationService} hold the shared
 * domain rules. This layer enforces policy and calls the DAOs; it never touches
 * sockets or SQL directly.
 */
package server.control;
