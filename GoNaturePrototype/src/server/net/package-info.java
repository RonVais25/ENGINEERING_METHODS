/**
 * Server networking: the OCSF-style TCP server that accepts client connections,
 * tracks each {@code ClientSession}, routes incoming {@code ClientRequest}s to
 * the domain layer via {@code RequestRouter}, and reports lifecycle events
 * through the {@link server.net.ServerListener} callback interface.
 */
package server.net;
