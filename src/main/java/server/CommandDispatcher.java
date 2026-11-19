package server;

import enums.ClientType;
import domains.Transporter;
import protocol.EventMessage;
import protocol.Request;
import protocol.Response;
import service.CargoService;
import service.CargoServiceImpl;
import service.EventLogService;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class CommandDispatcher {
    private final ClientRegistry registry;
    private final TransportRegistry transports;
    private final CargoService cargo;
    private final EventLogService eventLog;

    public CommandDispatcher(ClientRegistry registry) {
        this(registry, new TransportRegistry());
    }

    public CommandDispatcher(ClientRegistry registry, TransportRegistry transports) {
        this(registry, transports, new CargoServiceImpl(), new EventLogService());
    }

    public CommandDispatcher(ClientRegistry registry, TransportRegistry transports,
                             CargoService cargo, EventLogService eventLog) {
        this.registry = registry;
        this.transports = transports;
        this.cargo = cargo;
        this.eventLog = eventLog;
    }

    public Response dispatch(Request request, ClientType clientType) {
        String command = request.command().toUpperCase(Locale.ROOT);
        if (Set.of("LOAD_CARGO", "UNLOAD_CARGO", "TRANSFER_CARGO").contains(command)) {
            try {
                return cargoCommand(request, command);
            } catch (IllegalArgumentException | IllegalStateException e) {
                return new Response(request.requestId(), false, e.getMessage(), List.of());
            }
        }
        if (!request.arguments().isEmpty()) {
            return new Response(request.requestId(), false, "this command does not take arguments", List.of());
        }
        // permissions belong here as well as in the cli, clients can send their own requests
        return switch (command) {
            case "PING" -> new Response(request.requestId(), true, "pong", List.of());
            case "QUIT" -> new Response(request.requestId(), true, "goodbye", List.of());
            case "VIEW_TRANSPORTS" -> new Response(request.requestId(), true, "transports",
                    transports.getAll().stream().map(this::describeTransport).toList());
            case "VIEW_EVENTS" -> new Response(request.requestId(), true, "events",
                    eventLog.getEvents().stream().map(event -> event.timestamp() + " "
                            + event.eventType() + " " + event.details()).toList());
            case "LIST_CLIENTS" -> clientType == ClientType.ADMIN
                    ? new Response(request.requestId(), true, "connected clients", registry.getConnectedClients())
                    : new Response(request.requestId(), false, "admin role required", List.of());
            default -> new Response(request.requestId(), false, "unknown command", List.of());
        };
    }

    private Response cargoCommand(Request request, String command) {
        Map<String, String> arguments = request.arguments();
        Set<String> expected = command.equals("TRANSFER_CARGO")
                ? Set.of("source", "destination", "amount") : Set.of("transport", "amount");
        if (!arguments.keySet().equals(expected) || arguments.values().stream().anyMatch(String::isBlank)) {
            throw new IllegalArgumentException("required arguments: " + expected);
        }
        int amount;
        try {
            amount = Integer.parseInt(arguments.get("amount"));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("amount must be a whole number within the integer range");
        }
        // resolve ids here, the cargo service still owns capacity, state and transfer validation
        switch (command) {
            case "LOAD_CARGO" -> cargo.loadCargo(transports.get(arguments.get("transport")), amount);
            case "UNLOAD_CARGO" -> cargo.unloadCargo(transports.get(arguments.get("transport")), amount);
            case "TRANSFER_CARGO" -> cargo.transferCargo(transports.get(arguments.get("source")),
                    transports.get(arguments.get("destination")), amount);
            default -> throw new IllegalArgumentException("unknown cargo command");
        }
        Map<String, String> details = new HashMap<>(arguments);
        details.put("requestId", request.requestId());
        EventMessage event = EventMessage.now(command, details);
        // failures return before this point, so only successful changes get a log entry
        eventLog.record(event);
        // cargo has released its locks before any socket writes happen
        registry.broadcast(event);
        return new Response(request.requestId(), true, "cargo operation completed", List.of());
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
