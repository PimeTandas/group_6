package server;

import enums.ClientType;
import domains.Transporter;
import enums.TransportState;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import protocol.Request;
import protocol.Response;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class CommandDispatcherTest {
    private final CommandDispatcher dispatcher = new CommandDispatcher(new ClientRegistry());

    @Test
    void repliesToPingWithTheSameRequestId() {
        Response reply = dispatcher.dispatch(new Request("r1", "PING", Map.of()), ClientType.CLIENT);
        assertTrue(reply.success());
        assertEquals("r1", reply.requestId());
        assertEquals("pong", reply.message());
    }

    @Test
    void onlyAdminsCanListClients() {
        Request request = new Request("r2", "LIST_CLIENTS", Map.of());
        assertFalse(dispatcher.dispatch(request, ClientType.CLIENT).success());
        assertTrue(dispatcher.dispatch(request, ClientType.ADMIN).success());
    }

    @Test
    void rejectsUnknownCommandsAndUnexpectedArguments() {
        assertFalse(dispatcher.dispatch(new Request("r3", "NOPE", Map.of()), ClientType.CLIENT).success());
        assertFalse(dispatcher.dispatch(new Request("r4", "PING", Map.of("extra", "1")), ClientType.CLIENT).success());
    }

    @Test
    void bothRolesCanViewTheSharedTransportState() {
        TransportRegistry transports = new TransportRegistry();
        Transporter transport = new Transporter("t1", TransportState.DOCKED, null, 100, 20,
                LocalDateTime.of(2026, 11, 30, 12, 0), null);
        transports.register(transport);
        CommandDispatcher shared = new CommandDispatcher(new ClientRegistry(), transports);
        Request request = new Request("view", "VIEW_TRANSPORTS", Map.of());

        for (ClientType role : ClientType.values()) {
            Response reply = shared.dispatch(request, role);
            assertTrue(reply.success());
            assertEquals("view", reply.requestId());
            assertEquals(List.of("t1 state=DOCKED cargo=20/100 bay=none team=none departure=2026-11-30T12:00"), reply.data());
        }
        synchronized (transport) {
            transport.setCurrentQuantity(40);
        }
        assertTrue(shared.dispatch(request, ClientType.CLIENT).data().get(0).contains("cargo=40/100"));
    }

    @Test
    void emptyTransportRegistryReturnsAnEmptyList() {
        Response reply = dispatcher.dispatch(new Request("view", "VIEW_TRANSPORTS", Map.of()), ClientType.CLIENT);
        assertTrue(reply.success());
        assertTrue(reply.data().isEmpty());
    }
}
