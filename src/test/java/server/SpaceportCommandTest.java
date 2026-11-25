package server;

import domains.DockingBay;
import domains.MaintenanceTeam;
import enums.BayStatus;
import enums.ClientType;
import enums.MaintenanceStatus;
import org.junit.jupiter.api.Test;
import protocol.Request;
import protocol.Response;
import service.CargoServiceImpl;
import service.EventLogService;
import service.SpaceportService;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SpaceportCommandTest {
    private final EventLogService log = new EventLogService();
    private final SpaceportService spaceport = new SpaceportService(new TransportRegistry(), new CargoServiceImpl(), log,
            List.of(new DockingBay("b1", BayStatus.AVAILABLE, null)),
            List.of(new MaintenanceTeam("m1", MaintenanceStatus.AVAILABLE, null)));
    private final CommandDispatcher dispatcher = new CommandDispatcher(new ClientRegistry(), spaceport);
    private final Map<String, String> validTransport = Map.of("transport", "t1", "capacity", "100",
            "departure", "2026-11-30T17:00");

    private Response send(String command, Map<String, String> arguments, ClientType role) {
        return dispatcher.dispatch(new Request("request", command, arguments), role);
    }

    @Test
    void onlyAdminsCanAddTransportsAndSuccessfulAdditionIsShared() {
        assertFalse(send("ADD_TRANSPORT", validTransport, ClientType.CLIENT).success());
        assertTrue(spaceport.getTransports().isEmpty());
        assertTrue(log.getEvents().isEmpty());
        Response added = send("ADD_TRANSPORT", validTransport, ClientType.ADMIN);
        assertTrue(added.success());
        assertEquals("request", added.requestId());
        assertEquals(List.of("t1 state=WAITING cargo=0/100 bay=none team=none departure=2026-11-30T17:00"),
                send("VIEW_TRANSPORTS", Map.of(), ClientType.CLIENT).data());
        assertFalse(send("ADD_TRANSPORT", validTransport, ClientType.ADMIN).success());
        assertEquals(1, log.getEvents().size());
    }

    @Test
    void rejectsMissingArgumentsInvalidCapacityAndInvalidDeparture() {
        for (Map<String, String> arguments : List.of(Map.of("transport", "t1"),
                Map.of("transport", "t1", "capacity", "2147483648", "departure", "2026-11-30T17:00"),
                Map.of("transport", "t1", "capacity", "-1", "departure", "2026-11-30T17:00"),
                Map.of("transport", "t1", "capacity", "100", "departure", "tomorrow"),
                Map.of("transport", "t1", "capacity", "100", "departure", "2026-02-30T17:00"))) {
            assertFalse(send("ADD_TRANSPORT", arguments, ClientType.ADMIN).success());
        }
        assertTrue(spaceport.getTransports().isEmpty());
        assertTrue(log.getEvents().isEmpty());
    }

    @Test
    void bothRolesCanViewResourcesAndExtraArgumentsAreRejected() {
        for (ClientType role : ClientType.values()) {
            assertEquals(List.of("b1 status=AVAILABLE transport=none"), send("VIEW_BAYS", Map.of(), role).data());
            assertEquals(List.of("m1 status=AVAILABLE transport=none"), send("VIEW_TEAMS", Map.of(), role).data());
        }
        assertFalse(send("VIEW_BAYS", Map.of("extra", "x"), ClientType.CLIENT).success());
        assertTrue(log.getEvents().isEmpty());
    }
}
