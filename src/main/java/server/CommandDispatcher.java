package server;

import enums.ClientType;
import protocol.EventMessage;
import protocol.Request;
import protocol.Response;
import service.CargoService;
import service.CargoServiceImpl;
import service.EventLogService;
import service.SpaceportService;
import java.time.LocalDateTime;
import java.time.DateTimeException;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class CommandDispatcher {
    private final ClientRegistry registry;
    private final SpaceportService spaceport;

    public CommandDispatcher(ClientRegistry registry) {
        this(registry, new TransportRegistry());
    }

    public CommandDispatcher(ClientRegistry registry, TransportRegistry transports) {
        this(registry, transports, new CargoServiceImpl(), new EventLogService());
    }

    public CommandDispatcher(ClientRegistry registry, TransportRegistry transports,
                             CargoService cargo, EventLogService eventLog) {
        this(registry, new SpaceportService(transports, cargo, eventLog));
    }

    public CommandDispatcher(ClientRegistry registry, SpaceportService spaceport) {
        this.registry = registry;
        this.spaceport = spaceport;
    }

    public Response dispatch(Request request, ClientType clientType) {
        String command = request.command().toUpperCase(Locale.ROOT);
        if (command.equals("ADD_TRANSPORT")) {
            if (clientType != ClientType.ADMIN) {
                return new Response(request.requestId(), false, "admin role required", List.of());
            }
            try {
                requireArguments(request, Set.of("transport", "capacity", "departure"));
                EventMessage event = spaceport.addTransport(request.arguments().get("transport"),
                        Integer.parseInt(request.arguments().get("capacity")),
                        LocalDateTime.parse(request.arguments().get("departure")), request.requestId());
                registry.broadcast(event);
                return new Response(request.requestId(), true, "transport added", List.of());
            } catch (DateTimeException e) {
                return new Response(request.requestId(), false, "departure must use yyyy-MM-ddTHH:mm[:ss]", List.of());
            } catch (IllegalArgumentException | IllegalStateException e) {
                return new Response(request.requestId(), false, e.getMessage(), List.of());
            }
        }
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
                    spaceport.getTransports().stream().map(this::describeTransport).toList());
            case "VIEW_BAYS" -> new Response(request.requestId(), true, "docking bays",
                    spaceport.getBays().stream().map(bay -> bay.id() + " status=" + bay.status()
                            + " transport=" + orNone(bay.transportId())).toList());
            case "VIEW_TEAMS" -> new Response(request.requestId(), true, "maintenance teams",
                    spaceport.getTeams().stream().map(team -> team.id() + " status=" + team.status()
                            + " transport=" + orNone(team.transportId())).toList());
            case "VIEW_EVENTS" -> new Response(request.requestId(), true, "events",
                    spaceport.getEvents().stream().map(event -> event.timestamp() + " "
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
        requireArguments(request, expected);
        int amount;
        try {
            amount = Integer.parseInt(arguments.get("amount"));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("amount must be a whole number within the integer range");
        }
        EventMessage event = switch (command) {
            case "LOAD_CARGO" -> spaceport.loadCargo(arguments.get("transport"), amount, request.requestId());
            case "UNLOAD_CARGO" -> spaceport.unloadCargo(arguments.get("transport"), amount, request.requestId());
            case "TRANSFER_CARGO" -> spaceport.transferCargo(arguments.get("source"),
                    arguments.get("destination"), amount, request.requestId());
            default -> throw new IllegalArgumentException("unknown cargo command");
        };
        // cargo has released its locks before any socket writes happen
        registry.broadcast(event);
        return new Response(request.requestId(), true, "cargo operation completed", List.of());
    }

    private void requireArguments(Request request, Set<String> expected) {
        if (!request.arguments().keySet().equals(expected)
                || request.arguments().values().stream().anyMatch(String::isBlank)) {
            throw new IllegalArgumentException("required arguments: " + expected);
        }
    }

    private String describeTransport(SpaceportService.TransportView transport) {
        return transport.id() + " state=" + transport.state()
                + " cargo=" + transport.quantity() + "/" + transport.capacity()
                + " bay=" + orNone(transport.bayId()) + " team=" + orNone(transport.teamId())
                + " departure=" + transport.departure();
    }

    private String orNone(String id) {
        return id == null ? "none" : id;
    }
}
