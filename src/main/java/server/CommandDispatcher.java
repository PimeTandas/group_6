package server;

import enums.ClientType;
import protocol.Request;
import protocol.Response;
import java.util.List;
import java.util.Locale;

public class CommandDispatcher {
    private final ClientRegistry registry;

    public CommandDispatcher(ClientRegistry registry) {
        this.registry = registry;
    }

    public Response dispatch(Request request, ClientType clientType) {
        if (!request.arguments().isEmpty()) {
            return new Response(request.requestId(), false, "this command does not take arguments", List.of());
        }
        // permissions belong here as well as in the cli, clients can send their own requests
        return switch (request.command().toUpperCase(Locale.ROOT)) {
            case "PING" -> new Response(request.requestId(), true, "pong", List.of());
            case "QUIT" -> new Response(request.requestId(), true, "goodbye", List.of());
            case "LIST_CLIENTS" -> clientType == ClientType.ADMIN
                    ? new Response(request.requestId(), true, "connected clients", registry.getConnectedClients())
                    : new Response(request.requestId(), false, "admin role required", List.of());
            default -> new Response(request.requestId(), false, "unknown command", List.of());
        };
    }
}
