package server;

import protocol.EventMessage;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class ClientRegistry {
    private final ConcurrentMap<String, ClientHandler> clients = new ConcurrentHashMap<>();

    public boolean register(String name, ClientHandler handler) {
        // checking then adding separately would let two terminals claim the same name
        return clients.putIfAbsent(name, handler) == null;
    }

    public void remove(String name, ClientHandler handler) {
        clients.remove(name, handler);
    }

    public List<String> getConnectedClients() {
        return clients.keySet().stream().sorted().toList();
    }

    public void broadcast(EventMessage event) {
        clients.values().forEach(handler -> handler.send(event));
    }
}
