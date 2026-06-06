package server.net;

import java.net.Socket;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class ClientConnectionRegistry {
    private Set<Socket> clients = Collections.synchronizedSet(new HashSet<>());
    public void add(Socket socket) { clients.add(socket); }
    public void remove(Socket socket) { clients.remove(socket); }
    public int count() { return clients.size(); }
}
