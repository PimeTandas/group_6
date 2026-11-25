package server;

import domains.DockingBay;
import domains.MaintenanceTeam;
import domains.Transporter;
import enums.BayStatus;
import enums.ClientType;
import enums.MaintenanceStatus;
import enums.TransportState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import protocol.Request;
import protocol.Response;
import service.CargoServiceImpl;
import service.EventLogService;
import service.SpaceportService;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ResourceCommandTest {
    private final TransportRegistry transports = new TransportRegistry();
    private final EventLogService log = new EventLogService();
    private final SpaceportService spaceport = new SpaceportService(transports, new CargoServiceImpl(), log,
            List.of(new DockingBay("b1", BayStatus.AVAILABLE, null),
                    new DockingBay("b2", BayStatus.AVAILABLE, null),
                    new DockingBay("spare", BayStatus.AVAILABLE, null)),
            List.of(new MaintenanceTeam("m1", MaintenanceStatus.AVAILABLE, null)));
    private final CommandDispatcher dispatcher = new CommandDispatcher(new ClientRegistry(), spaceport);
    private int requestNumber;

    @BeforeEach
    void setUp() {
        for (String id : List.of("a", "b")) {
            transports.register(new Transporter(id, TransportState.WAITING, null, 100, 0,
                    LocalDateTime.of(2026, 11, 30, 17, 0), null));
        }
    }

    private Response send(String command, Map<String, String> args, ClientType role) {
        String id = "resource-" + ++requestNumber;
        Response response = dispatcher.dispatch(new Request(id, command, args), role);
        assertEquals(id, response.requestId());
        return response;
    }

    @Test
    void dockingCargoExchangeAndMaintenanceWorkThroughCommands() {
        assertTrue(send("ALLOCATE_BAY", Map.of("transport", "a", "bay", "b1"), ClientType.CLIENT).success());
        assertTrue(send("ALLOCATE_BAY", Map.of("transport", "b", "bay", "b2"), ClientType.CLIENT).success());
        assertTrue(send("LOAD_CARGO", Map.of("transport", "a", "amount", "20"), ClientType.CLIENT).success());
        assertTrue(send("EXCHANGE_BAYS", Map.of("first", "a", "second", "b"), ClientType.CLIENT).success());
        assertEquals("b2", transports.get("a").getDockingBay().getBayId());
        assertSame(transports.get("a"), transports.get("a").getDockingBay().getOccupant());
        assertSame(transports.get("b"), transports.get("b").getDockingBay().getOccupant());
        assertTrue(send("ASSIGN_TEAM", Map.of("transport", "a", "team", "m1"), ClientType.CLIENT).success());
        assertEquals(TransportState.SERVICING, transports.get("a").getState());
        assertTrue(send("COMPLETE_MAINTENANCE", Map.of("transport", "a"), ClientType.CLIENT).success());
        assertEquals(TransportState.READY, transports.get("a").getState());
        assertEquals(20, transports.get("a").getCurrentQuantity());
        assertNull(transports.get("a").getMaintenanceTeam());
        assertEquals(MaintenanceStatus.AVAILABLE, spaceport.getTeams().get(0).status());
        assertNull(spaceport.getTeams().get(0).transportId());
        assertTrue(send("CLOSE_BAY", Map.of("bay", "spare"), ClientType.ADMIN).success());
        assertEquals(BayStatus.CLOSED, spaceport.getBays().get(2).status());
        assertTrue(send("REOPEN_BAY", Map.of("bay", "spare"), ClientType.ADMIN).success());
        assertEquals(BayStatus.AVAILABLE, spaceport.getBays().get(2).status());
        assertEquals(List.of("ALLOCATE_BAY", "ALLOCATE_BAY", "LOAD_CARGO", "EXCHANGE_BAYS", "ASSIGN_TEAM",
                "COMPLETE_MAINTENANCE", "CLOSE_BAY", "REOPEN_BAY"),
                log.getEvents().stream().map(event -> event.eventType()).toList());
    }

    @Test
    void permissionsAndInvalidResourceOperationsLeaveAllocationsAndLogUnchanged() {
        assertFalse(send("CLOSE_BAY", Map.of("bay", "spare"), ClientType.CLIENT).success());
        assertFalse(send("REOPEN_BAY", Map.of("bay", "spare"), ClientType.CLIENT).success());
        assertFalse(send("ALLOCATE_BAY", Map.of("transport", "a"), ClientType.CLIENT).success());
        assertFalse(send("ALLOCATE_BAY", Map.of("transport", "a", "bay", "missing"), ClientType.CLIENT).success());
        assertTrue(log.getEvents().isEmpty());
        assertTrue(send("ALLOCATE_BAY", Map.of("transport", "a", "bay", "b1"), ClientType.CLIENT).success());
        assertFalse(send("ALLOCATE_BAY", Map.of("transport", "b", "bay", "b1"), ClientType.CLIENT).success());
        assertFalse(send("CLOSE_BAY", Map.of("bay", "b1"), ClientType.ADMIN).success());
        assertFalse(send("COMPLETE_MAINTENANCE", Map.of("transport", "a"), ClientType.CLIENT).success());
        assertFalse(send("EXCHANGE_BAYS", Map.of("first", "a", "second", "b"), ClientType.CLIENT).success());
        assertTrue(send("ALLOCATE_BAY", Map.of("transport", "b", "bay", "b2"), ClientType.CLIENT).success());
        assertTrue(send("ASSIGN_TEAM", Map.of("transport", "a", "team", "m1"), ClientType.CLIENT).success());
        assertFalse(send("ASSIGN_TEAM", Map.of("transport", "b", "team", "m1"), ClientType.CLIENT).success());
        assertFalse(send("EXCHANGE_BAYS", Map.of("first", "a", "second", "b"), ClientType.CLIENT).success());
        assertEquals("b1", transports.get("a").getDockingBay().getBayId());
        assertEquals("b2", transports.get("b").getDockingBay().getBayId());
        assertEquals(TransportState.SERVICING, transports.get("a").getState());
        assertEquals(TransportState.DOCKED, transports.get("b").getState());
        assertSame(transports.get("a"), transports.get("a").getMaintenanceTeam().getTransporter());
        assertNull(transports.get("b").getMaintenanceTeam());
        assertEquals(3, log.getEvents().size());
    }
}
