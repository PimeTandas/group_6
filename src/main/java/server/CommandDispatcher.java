package server;

import enums.ClientType;
import domains.Transporter;
import protocol.Request;
import protocol.Response;
import java.util.List;
import java.util.Locale;

public class CommandDispatcher {
    private final ClientRegistry registry;
    private final TransportRegistry transports;

    public CommandDispatcher(ClientRegistry registry) {
        this(registry, new TransportRegistry());
    }

    public CommandDispatcher(ClientRegistry registry, TransportRegistry transports) {
        this.registry = registry;
        this.transports = transports;
    }

    public Response dispatch(Request request, ClientType clientType) {
        if (!request.arguments().isEmpty()) {
            return new Response(request.requestId(), false, "this command does not take arguments", List.of());
        }
        // permissions belong here as well as in the cli, clients can send their own requests
        return switch (request.command().toUpperCase(Locale.ROOT)) {
            case "PING" -> new Response(request.requestId(), true, "pong", List.of());
            case "QUIT" -> new Response(request.requestId(), true, "goodbye", List.of());
            case "VIEW_TRANSPORTS" -> new Response(request.requestId(), true, "transports",
                    transports.getAll().stream().map(this::describeTransport).toList());
            case "LIST_CLIENTS" -> clientType == ClientType.ADMIN
                    ? new Response(request.requestId(), true, "connected clients", registry.getConnectedClients())
                    : new Response(request.requestId(), false, "admin role required", List.of());
            default -> new Response(request.requestId(), false, "unknown command", List.of());
        };
    }

    private String describeTransport(Transporter transport) {
        // cargo uses this same lock, so quantity and state are read together
        synchronized (transport) {
            return transport.getTransporterId() + " state=" + transport.getState()
                    + " cargo=" + transport.getCurrentQuantity() + "/" + transport.getMaxCapacity()
                    + " bay=" + (transport.getDockingBay() == null ? "none" : transport.getDockingBay().getBayId())
                    + " team=" + (transport.getMaintenanceTeam() == null ? "none" : transport.getMaintenanceTeam().getMaintenanceId())
                    + " departure=" + transport.getDepartureTime();
        }
    }
}
