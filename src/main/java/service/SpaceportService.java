package service;

import domains.Transporter;
import domains.DockingBay;
import domains.MaintenanceTeam;
import enums.BayStatus;
import enums.MaintenanceStatus;
import enums.TransportState;
import protocol.EventMessage;
import server.TransportRegistry;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Comparator;
import java.util.HashSet;

public class SpaceportService {
    private final TransportRegistry transports;
    private final CargoService cargo;
    private final EventLogService eventLog;
    private final List<DockingBay> bays;
    private final List<MaintenanceTeam> teams;

    public SpaceportService(TransportRegistry transports) {
        this(transports, new CargoServiceImpl(), new EventLogService());
    }

    public SpaceportService(TransportRegistry transports, CargoService cargo, EventLogService eventLog) {
        this(transports, cargo, eventLog, List.of(), List.of());
    }

    public SpaceportService(TransportRegistry transports, CargoService cargo, EventLogService eventLog,
                            List<DockingBay> bays, List<MaintenanceTeam> teams) {
        this.transports = Objects.requireNonNull(transports);
        this.cargo = Objects.requireNonNull(cargo);
        this.eventLog = Objects.requireNonNull(eventLog);
        this.bays = List.copyOf(bays);
        this.teams = List.copyOf(teams);
        validateIds(this.bays.stream().map(DockingBay::getBayId).toList());
        validateIds(this.teams.stream().map(MaintenanceTeam::getMaintenanceId).toList());
    }

    public EventMessage addTransport(String id, int capacity, LocalDateTime departure, String requestId) {
        if (id == null || id.isBlank() || !id.equals(id.trim())) {
            throw new IllegalArgumentException("transport needs an id without surrounding spaces");
        }
        if (capacity < 0 || departure == null) {
            throw new IllegalArgumentException("capacity must be non-negative and departure time is required");
        }
        EventMessage event = EventMessage.now("ADD_TRANSPORT", Map.of("transport", id,
                "capacity", Integer.toString(capacity), "departure", departure.toString(), "requestId", requestId));
        Transporter transport = new Transporter(id, TransportState.WAITING, null, capacity, 0, departure, null);
        synchronized (transport) {
            transports.register(transport);
            eventLog.record(event);
        }
        return event;
    }

    public EventMessage loadCargo(String id, int amount, String requestId) {
        Transporter transport = transports.get(id);
        synchronized (transport) {
            EventMessage event = cargoEvent("LOAD_CARGO", id, amount, requestId);
            cargo.loadCargo(transport, amount);
            eventLog.record(event);
            return event;
        }
    }

    public EventMessage unloadCargo(String id, int amount, String requestId) {
        Transporter transport = transports.get(id);
        synchronized (transport) {
            EventMessage event = cargoEvent("UNLOAD_CARGO", id, amount, requestId);
            cargo.unloadCargo(transport, amount);
            eventLog.record(event);
            return event;
        }
    }

    public EventMessage transferCargo(String sourceId, String destinationId, int amount, String requestId) {
        Transporter source = transports.get(sourceId);
        Transporter destination = transports.get(destinationId);
        Transporter first = sourceId.compareTo(destinationId) < 0 ? source : destination;
        Transporter second = first == source ? destination : source;
        // use the cargo service's lock order, including while the change is logged
        synchronized (first) {
            synchronized (second) {
                EventMessage event = EventMessage.now("TRANSFER_CARGO", Map.of("source", sourceId,
                        "destination", destinationId, "amount", Integer.toString(amount), "requestId", requestId));
                cargo.transferCargo(source, destination, amount);
                eventLog.record(event);
                return event;
            }
        }
    }

    public List<TransportView> getTransports() {
        return transports.getAll().stream().map(transport -> {
            synchronized (transport) {
                return new TransportView(transport.getTransporterId(), transport.getState(),
                        transport.getMaxCapacity(), transport.getCurrentQuantity(),
                        transport.getDockingBay() == null ? null : transport.getDockingBay().getBayId(),
                        transport.getMaintenanceTeam() == null ? null : transport.getMaintenanceTeam().getMaintenanceId(),
                        transport.getDepartureTime());
            }
        }).toList();
    }

    public List<EventMessage> getEvents() {
        return eventLog.getEvents();
    }

    public List<BayView> getBays() {
        return bays.stream().sorted(Comparator.comparing(DockingBay::getBayId)).map(bay -> {
            synchronized (bay) {
                return new BayView(bay.getBayId(), bay.getStatus(),
                        bay.getOccupant() == null ? null : bay.getOccupant().getTransporterId());
            }
        }).toList();
    }

    public List<TeamView> getTeams() {
        return teams.stream().sorted(Comparator.comparing(MaintenanceTeam::getMaintenanceId)).map(team -> {
            synchronized (team) {
                return new TeamView(team.getMaintenanceId(), team.getMaintenanceStatus(),
                        team.getTransporter() == null ? null : team.getTransporter().getTransporterId());
            }
        }).toList();
    }

    private void validateIds(List<String> ids) {
        if (ids.stream().anyMatch(id -> id == null || id.isBlank()) || new HashSet<>(ids).size() != ids.size()) {
            throw new IllegalArgumentException("resource ids must be non-blank and unique");
        }
    }

    private EventMessage cargoEvent(String type, String id, int amount, String requestId) {
        return EventMessage.now(type, Map.of("transport", id, "amount", Integer.toString(amount), "requestId", requestId));
    }

    // views contain values rather than the mutable objects locked by the services
    public record TransportView(String id, TransportState state, int capacity, int quantity,
                                String bayId, String teamId, LocalDateTime departure) {
    }

    public record BayView(String id, BayStatus status, String transportId) { }

    public record TeamView(String id, MaintenanceStatus status, String transportId) { }
}
