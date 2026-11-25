package server;

import domains.Transporter;

import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class TransportRegistry {
    private final ConcurrentMap<String, Transporter> transports = new ConcurrentHashMap<>();

    public void register(Transporter transport) {
        if (transport == null || transport.getTransporterId() == null
                || transport.getTransporterId().isBlank()) {
            throw new IllegalArgumentException("transport needs an id");
        }
        // don't replace an existing object, another service might already be locking it
        if (transports.putIfAbsent(transport.getTransporterId(), transport) != null) {
            throw new IllegalArgumentException("transport id already exists: " + transport.getTransporterId());
        }
    }

    public Transporter get(String id) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("transport id is required");
        }
        Transporter transport = transports.get(id);
        if (transport == null) {
            throw new IllegalArgumentException("unknown transport: " + id);
        }
        return transport;
    }

    public List<Transporter> getAll() {
        // copy the collection, but keep the same transport objects shared by the services
        return transports.values().stream()
                .sorted(Comparator.comparing(Transporter::getTransporterId))
                .toList();
    }
}
