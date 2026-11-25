package server;

import domains.Transporter;
import enums.ClientType;
import enums.TransportState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import protocol.Request;
import protocol.Response;
import service.CargoServiceImpl;
import service.EventLogService;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CargoCommandTest {
    private final TransportRegistry transports = new TransportRegistry();
    private final EventLogService eventLog = new EventLogService();
    private final CommandDispatcher dispatcher = new CommandDispatcher(new ClientRegistry(), transports,
            new CargoServiceImpl(), eventLog);

    @BeforeEach
    void setUp() {
        transports.register(new Transporter("a", TransportState.DOCKED, null, 100, 20,
                LocalDateTime.of(2026, 11, 30, 12, 0), null));
        transports.register(new Transporter("b", TransportState.READY, null, 100, 10,
                LocalDateTime.of(2026, 11, 30, 12, 0), null));
    }

    private Response send(String id, String command, Map<String, String> arguments) {
        return dispatcher.dispatch(new Request(id, command, arguments), ClientType.CLIENT);
    }

    @Test
    void runsCargoCommandsAndRecordsEachSuccessfulChangeOnce() {
        assertTrue(send("load", "LOAD_CARGO", Map.of("transport", "a", "amount", "10")).success());
        assertTrue(send("unload", "UNLOAD_CARGO", Map.of("transport", "a", "amount", "5")).success());
        assertTrue(send("transfer", "TRANSFER_CARGO", Map.of("source", "a", "destination", "b", "amount", "15")).success());
        assertEquals(10, transports.get("a").getCurrentQuantity());
        assertEquals(25, transports.get("b").getCurrentQuantity());
        assertEquals(3, eventLog.getEvents().size());
        assertEquals("transfer", eventLog.getEvents().get(2).details().get("requestId"));
        assertEquals("TRANSFER_CARGO", eventLog.getEvents().get(2).eventType());
        Response events = send("events", "VIEW_EVENTS", Map.of());
        assertTrue(events.success());
        assertEquals(3, events.data().size());
        assertEquals(3, eventLog.getEvents().size());
    }

    @Test
    void invalidCargoRequestsLeaveStateAndLogUnchanged() {
        for (String amount : List.of("0", "-1", "1.5", "abc", "2147483648", "101")) {
            Response reply = send("bad-amount", "LOAD_CARGO", Map.of("transport", "a", "amount", amount));
            assertAll("amount: " + amount,
                    () -> assertFalse(reply.success()),
                    () -> assertEquals("bad-amount", reply.requestId()),
                    () -> assertEquals(20, transports.get("a").getCurrentQuantity()),
                    () -> assertTrue(eventLog.getEvents().isEmpty()));
        }
        assertFalse(send("missing", "LOAD_CARGO", Map.of("transport", "a")).success());
        assertFalse(send("extra", "LOAD_CARGO", Map.of("transport", "a", "amount", "1", "extra", "x")).success());
        assertFalse(send("unknown", "LOAD_CARGO", Map.of("transport", "missing", "amount", "1")).success());
        assertFalse(send("blank", "LOAD_CARGO", Map.of("transport", " ", "amount", "1")).success());
        transports.register(new Transporter("waiting", TransportState.WAITING, null, 100, 0,
                LocalDateTime.of(2026, 11, 30, 12, 0), null));
        assertFalse(send("waiting", "LOAD_CARGO", Map.of("transport", "waiting", "amount", "1")).success());
        assertEquals(0, transports.get("waiting").getCurrentQuantity());
        assertEquals(20, transports.get("a").getCurrentQuantity());
        assertTrue(eventLog.getEvents().isEmpty());
    }

    @Test
    void failedTransfersDoNotChangeEitherTransportOrLog() {
        assertFalse(send("too-much", "TRANSFER_CARGO", Map.of("source", "a", "destination", "b", "amount", "21")).success());
        assertFalse(send("same", "TRANSFER_CARGO", Map.of("source", "a", "destination", "a", "amount", "1")).success());
        assertFalse(send("missing", "TRANSFER_CARGO", Map.of("source", "a", "destination", "missing", "amount", "1")).success());
        assertEquals(20, transports.get("a").getCurrentQuantity());
        assertEquals(10, transports.get("b").getCurrentQuantity());
        assertTrue(eventLog.getEvents().isEmpty());
    }

}
