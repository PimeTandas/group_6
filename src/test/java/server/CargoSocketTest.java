package server;

import domains.Transporter;
import enums.TransportState;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import protocol.*;
import service.CargoServiceImpl;
import service.ClientLogService;
import service.EventLogService;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

class CargoSocketTest {
    private final TransportRegistry transports = new TransportRegistry();
    private final ClientRegistry clients = new ClientRegistry();
    private final EventLogService eventLog = new EventLogService();
    private final CommandDispatcher dispatcher = new CommandDispatcher(clients, transports,
            new CargoServiceImpl(), eventLog);
    private final List<Socket> sockets = new ArrayList<>();
    private final List<Future<?>> handlers = new ArrayList<>();
    private ExecutorService pool;
    private ServerSocket listener;

    private final ClientLogService connectionLog = new ClientLogService() {
        public void logConnected(String name, enums.ClientType type) { }
        public void logDisconnected(String name, enums.ClientType type) { }
    };

    @BeforeEach
    void setUp() throws Exception {
        listener = new ServerSocket(0, 10, InetAddress.getLoopbackAddress());
        listener.setSoTimeout(3000);
        pool = Executors.newCachedThreadPool();
        transports.register(transport("a", 0));
        transports.register(transport("b", 0));
    }

    private Transporter transport(String id, int quantity) {
        return new Transporter(id, TransportState.READY, null, 100, quantity,
                LocalDateTime.of(2026, 11, 30, 12, 0), null);
    }

    @AfterEach
    void tearDown() throws Exception {
        try {
            for (Socket socket : sockets) socket.close();
            // get propagates failures from handler threads instead of hiding them in the pool
            for (Future<?> handler : handlers) handler.get(3, TimeUnit.SECONDS);
        } finally {
            listener.close();
            pool.shutdownNow();
            assertTrue(pool.awaitTermination(3, TimeUnit.SECONDS));
        }
    }

    private Terminal connect(String name) throws Exception {
        Socket socket = new Socket(InetAddress.getLoopbackAddress(), listener.getLocalPort());
        socket.setSoTimeout(3000);
        sockets.add(socket);
        Socket accepted = listener.accept();
        sockets.add(accepted);
        handlers.add(pool.submit(new ClientHandler(accepted, connectionLog, clients, dispatcher)));
        Terminal terminal = new Terminal(socket);
        terminal.output.println(name);
        terminal.output.println("CLIENT");
        assertTrue(terminal.readResponse().success());
        return terminal;
    }

    private static class Terminal {
        final BufferedReader input;
        final PrintWriter output;

        Terminal(Socket socket) throws Exception {
            input = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            output = new PrintWriter(socket.getOutputStream(), true, StandardCharsets.UTF_8);
        }

        Response request(String id, String command, Map<String, String> arguments) throws Exception {
            output.println(MessageCodec.encode(new Request(id, command, arguments)));
            Response response = readResponse();
            assertEquals(id, response.requestId());
            return response;
        }

        Response readResponse() throws Exception {
            // notifications may arrive before the response to our own command
            for (int i = 0; i < 1000; i++) {
                Message message = MessageCodec.decode(input.readLine());
                if (message instanceof Response response) return response;
            }
            throw new AssertionError("no response received");
        }

        EventMessage awaitEvent(String type, String requestId) throws Exception {
            for (int i = 0; i < 1000; i++) {
                Message message = MessageCodec.decode(input.readLine());
                if (message instanceof EventMessage event && event.eventType().equals(type)
                        && requestId.equals(event.details().get("requestId"))) return event;
            }
            throw new AssertionError("event not received");
        }
    }

    @Test
    void anotherTerminalSeesCargoChangesAndTheirEvents() throws Exception {
        Terminal operator = connect("operator");
        Terminal observer = connect("observer");
        assertTrue(operator.request("load", "LOAD_CARGO", Map.of("transport", "a", "amount", "25")).success());
        EventMessage event = observer.awaitEvent("LOAD_CARGO", "load");
        assertEquals("a", event.details().get("transport"));
        assertEquals("25", event.details().get("amount"));
        Response view = observer.request("view", "VIEW_TRANSPORTS", Map.of());
        assertTrue(view.data().get(0).contains("cargo=25/100"));
        assertTrue(operator.request("unload", "UNLOAD_CARGO", Map.of("transport", "a", "amount", "5")).success());
        observer.awaitEvent("UNLOAD_CARGO", "unload");
        assertFalse(operator.request("fail", "UNLOAD_CARGO", Map.of("transport", "a", "amount", "21")).success());
        Response log = observer.request("log", "VIEW_EVENTS", Map.of());
        assertTrue(log.success());
        assertEquals(2, log.data().size());
        assertEquals(20, transports.get("a").getCurrentQuantity());
        assertEquals(2, eventLog.getEvents().size());
    }

    @Test
    void simultaneousLoadsCannotExceedCapacity() throws Exception {
        Terminal first = connect("first");
        Terminal second = connect("second");
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<Integer> a = pool.submit(() -> load(first, "first", ready, start));
            Future<Integer> b = pool.submit(() -> load(second, "second", ready, start));
            assertTrue(ready.await(3, TimeUnit.SECONDS));
            start.countDown();
            int successful = a.get(10, TimeUnit.SECONDS) + b.get(10, TimeUnit.SECONDS);
            assertEquals(10, successful);
            assertEquals(100, transports.get("a").getCurrentQuantity());
            assertEquals(successful, eventLog.getEvents().size());
            assertEquals(successful, eventLog.getEvents().stream()
                    .map(event -> event.details().get("requestId")).distinct().count());
        } finally {
            start.countDown();
        }
    }

    private int load(Terminal terminal, String name, CountDownLatch ready, CountDownLatch start) throws Exception {
        ready.countDown();
        assertTrue(start.await(3, TimeUnit.SECONDS));
        int successful = 0;
        for (int i = 0; i < 10; i++) {
            if (terminal.request(name + i, "LOAD_CARGO", Map.of("transport", "a", "amount", "10")).success()) {
                successful++;
            }
        }
        return successful;
    }

    @Test
    void opposingTransfersConserveCargoWithoutDeadlock() throws Exception {
        synchronized (transports.get("a")) { transports.get("a").setCurrentQuantity(50); }
        synchronized (transports.get("b")) { transports.get("b").setCurrentQuantity(50); }
        Terminal first = connect("first");
        Terminal second = connect("second");
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<?> a = pool.submit(() -> transfer(first, "a", "b", ready, start));
            Future<?> b = pool.submit(() -> transfer(second, "b", "a", ready, start));
            assertTrue(ready.await(3, TimeUnit.SECONDS));
            start.countDown();
            a.get(10, TimeUnit.SECONDS);
            b.get(10, TimeUnit.SECONDS);
            assertEquals(50, transports.get("a").getCurrentQuantity());
            assertEquals(50, transports.get("b").getCurrentQuantity());
            assertEquals(80, eventLog.getEvents().size());
            assertEquals(80, eventLog.getEvents().stream()
                    .map(event -> event.details().get("requestId")).distinct().count());
        } finally {
            start.countDown();
        }
    }

    private Void transfer(Terminal terminal, String source, String destination,
                          CountDownLatch ready, CountDownLatch start) throws Exception {
        ready.countDown();
        assertTrue(start.await(3, TimeUnit.SECONDS));
        for (int i = 0; i < 40; i++) {
            assertTrue(terminal.request(source + i, "TRANSFER_CARGO",
                    Map.of("source", source, "destination", destination, "amount", "1")).success());
        }
        return null;
    }
}
