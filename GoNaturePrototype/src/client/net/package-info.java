/**
 * Client networking: the persistent TCP connection to the server and the event
 * bus that delivers server-pushed real-time updates. {@link client.net.ClientConnection}
 * owns the socket and request/response plumbing; {@link client.net.EventBus}
 * fans out asynchronous {@code ServerEvent}s to subscribed controllers.
 */
package client.net;
