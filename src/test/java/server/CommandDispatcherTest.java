package server;

import enums.ClientType;
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
}
