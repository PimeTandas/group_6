package server;

import enums.ClientType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import service.ClientLogService;
import protocol.*;

import java.io.IOException;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClientHandlerTest {

    private ServerSocket listener;
    private ExecutorService handlers;
    private final List<Socket> sockets = new ArrayList<>();
    private final BlockingQueue<String> events = new LinkedBlockingQueue<>();
    private final ClientRegistry registry = new ClientRegistry();

    private final ClientLogService log = new ClientLogService() {
        public void logConnected(String name, ClientType type) {
            events.add("connected " + name + " " + type);
        }

        public void logDisconnected(String name, ClientType type) {
            events.add("disconnected " + name + " " + type);
        }
    };

    @BeforeEach
    void setUp() throws IOException {
        listener = new ServerSocket(0, 10, InetAddress.getLoopbackAddress());
        listener.setSoTimeout(3000);
        handlers = Executors.newCachedThreadPool();
    }

    @AfterEach
    void tearDown() throws IOException, InterruptedException {
        try {
            for (Socket socket : sockets) {
                socket.close();
            }
        } finally {
            listener.close();
            handlers.shutdownNow();
            assertTrue(handlers.awaitTermination(3, TimeUnit.SECONDS));
        }
    }

    @ParameterizedTest
    @EnumSource(ClientType.class)
    void logsConnectionAndQuit(ClientType type) throws Exception {
        Socket client = connect();
        Future<?> handler = startHandler();
        PrintWriter output = new PrintWriter(client.getOutputStream(), true);

        output.println("terminal-1");
        output.println(type);
        assertEquals("connected terminal-1 " + type, events.poll(3, TimeUnit.SECONDS));

        output.println("QUIT");
        handler.get(3, TimeUnit.SECONDS);
        assertEquals("disconnected terminal-1 " + type, events.poll());
        assertNull(events.poll());
    }

    @Test
    void logsDisconnectWhenClientClosesOutput() throws Exception {
        Socket client = connect();
        Future<?> handler = startHandler();
        PrintWriter output = new PrintWriter(client.getOutputStream(), true);

        output.println("terminal-2");
        output.println("CLIENT");
        assertEquals("connected terminal-2 CLIENT", events.poll(3, TimeUnit.SECONDS));

        client.shutdownOutput();
        handler.get(3, TimeUnit.SECONDS);
        assertEquals("disconnected terminal-2 CLIENT", events.poll());
        assertNull(events.poll());
    }

    @Test
    void handlesMultipleClientsAtTheSameTime() throws Exception {
        int clientCount = 8;
        ExecutorService clients = Executors.newFixedThreadPool(clientCount);
        CountDownLatch ready = new CountDownLatch(clientCount);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch quit = new CountDownLatch(1);
        List<Future<?>> clientTasks = new ArrayList<>();
        List<Future<?>> handlerTasks = new ArrayList<>();
        List<String> recorded = new ArrayList<>();

        try {
            for (int i = 0; i < clientCount; i++) {
                Socket client = connect();
                handlerTasks.add(startHandler());
                String name = "terminal-" + i;
                clientTasks.add(clients.submit(() -> {
                    PrintWriter output = new PrintWriter(client.getOutputStream(), true);
                    ready.countDown();
                    assertTrue(start.await(3, TimeUnit.SECONDS));
                    output.println(name);
                    output.println("CLIENT");
                    assertTrue(quit.await(3, TimeUnit.SECONDS));
                    output.println("QUIT");
                    return null;
                }));
            }

            assertTrue(ready.await(3, TimeUnit.SECONDS));
            start.countDown();
            for (int i = 0; i < clientCount; i++) {
                String event = events.poll(3, TimeUnit.SECONDS);
                assertTrue(event != null && event.startsWith("connected "));
                recorded.add(event);
            }

            quit.countDown();
            for (Future<?> task : clientTasks) {
                task.get(3, TimeUnit.SECONDS);
            }
            for (Future<?> task : handlerTasks) {
                task.get(3, TimeUnit.SECONDS);
            }
            events.drainTo(recorded);

            assertEquals(clientCount * 2, recorded.size());
            for (int i = 0; i < clientCount; i++) {
                assertEquals(1, Collections.frequency(recorded, "connected terminal-" + i + " CLIENT"));
                assertEquals(1, Collections.frequency(recorded, "disconnected terminal-" + i + " CLIENT"));
            }
        } finally {
            start.countDown();
            quit.countDown();
            clients.shutdownNow();
            assertTrue(clients.awaitTermination(3, TimeUnit.SECONDS));
        }
    }

    private Socket connect() throws IOException {
        Socket client = new Socket(InetAddress.getLoopbackAddress(), listener.getLocalPort());
        client.setSoTimeout(3000);
        sockets.add(client);
        return client;
    }

    private Future<?> startHandler() throws IOException {
        Socket accepted = listener.accept();
        sockets.add(accepted);
        return handlers.submit(new ClientHandler(accepted, log, registry));
    }

    @Test
    void repliesToPingAndRecoversFromMalformedRequest() throws Exception {
        Socket client = connect();
        Future<?> handler = startHandler();
        PrintWriter output = new PrintWriter(client.getOutputStream(), true, StandardCharsets.UTF_8);
        BufferedReader input = reader(client);
        output.println("terminal-ping");
        output.println("CLIENT");
        assertTrue(readResponse(input).success());

        output.println("not a request");
        assertTrue(!readResponse(input).success());
        output.println(MessageCodec.encode(new Request("ping-1", "PING", Map.of())));
        Response pong = readResponse(input);
        assertEquals("ping-1", pong.requestId());
        assertEquals("pong", pong.message());
        assertTrue(pong.success());

        output.println(MessageCodec.encode(new Request("quit-1", "QUIT", Map.of())));
        assertEquals("quit-1", readResponse(input).requestId());
        handler.get(3, TimeUnit.SECONDS);
        assertNull(input.readLine());
        assertTrue(registry.getConnectedClients().isEmpty());
    }

    @Test
    void duplicateNameDoesNotRemoveOriginalClient() throws Exception {
        Socket first = connect();
        startHandler();
        PrintWriter firstOutput = new PrintWriter(first.getOutputStream(), true);
        BufferedReader firstInput = reader(first);
        firstOutput.println("same-name");
        firstOutput.println("CLIENT");
        assertTrue(readResponse(firstInput).success());

        Socket second = connect();
        Future<?> secondHandler = startHandler();
        PrintWriter secondOutput = new PrintWriter(second.getOutputStream(), true);
        secondOutput.println("same-name");
        secondOutput.println("ADMIN");
        assertTrue(!readResponse(reader(second)).success());
        secondHandler.get(3, TimeUnit.SECONDS);
        assertEquals(List.of("same-name"), registry.getConnectedClients());

        firstOutput.println(MessageCodec.encode(new Request("still-here", "PING", Map.of())));
        assertEquals("still-here", readResponse(firstInput).requestId());
    }

    @Test
    void broadcastsOtherClientsConnectingAndLeaving() throws Exception {
        Socket first = connect();
        startHandler();
        PrintWriter firstOutput = new PrintWriter(first.getOutputStream(), true);
        BufferedReader firstInput = reader(first);
        firstOutput.println("first");
        firstOutput.println("ADMIN");
        assertTrue(readResponse(firstInput).success());
        assertEquals("first", readEvent(firstInput).details().get("client"));

        Socket second = connect();
        Future<?> secondHandler = startHandler();
        PrintWriter secondOutput = new PrintWriter(second.getOutputStream(), true);
        secondOutput.println("second");
        secondOutput.println("CLIENT");
        assertTrue(readResponse(reader(second)).success());
        EventMessage connected = readEvent(firstInput);
        assertEquals("CLIENT_CONNECTED", connected.eventType());
        assertEquals("second", connected.details().get("client"));

        firstOutput.println(MessageCodec.encode(new Request("list", "LIST_CLIENTS", Map.of())));
        assertEquals(List.of("first", "second"), readResponse(firstInput).data());
        secondOutput.println("QUIT");
        secondHandler.get(3, TimeUnit.SECONDS);
        EventMessage disconnected = readEvent(firstInput);
        assertEquals("CLIENT_DISCONNECTED", disconnected.eventType());
        assertEquals("second", disconnected.details().get("client"));
    }

    @ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"", "not-a-role"})
    void rejectsInvalidHandshakeWithoutRegistering(String role) throws Exception {
        Socket client = connect();
        Future<?> handler = startHandler();
        PrintWriter output = new PrintWriter(client.getOutputStream(), true);
        output.println("invalid");
        output.println(role);
        BufferedReader input = reader(client);
        assertTrue(!readResponse(input).success());
        handler.get(3, TimeUnit.SECONDS);
        assertNull(input.readLine());
        assertTrue(registry.getConnectedClients().isEmpty());
        assertTrue(events.isEmpty());
    }

    private BufferedReader reader(Socket socket) throws IOException {
        return new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
    }

    @Test
    void receivesBackgroundEventsWhileCommandsAreBeingSent() throws Exception {
        Socket client = connect();
        startHandler();
        PrintWriter output = new PrintWriter(client.getOutputStream(), true);
        BufferedReader input = reader(client);
        output.println("listening");
        output.println("CLIENT");
        assertTrue(readResponse(input).success());
        readEvent(input);

        BlockingQueue<Message> received = new LinkedBlockingQueue<>();
        BlockingQueue<String> errors = new LinkedBlockingQueue<>();
        Future<?> listenerTask = handlers.submit(new client.ServerMessageListener(input, received::add, errors::add));
        int count = 40;
        CountDownLatch start = new CountDownLatch(1);
        Future<?> broadcasts = handlers.submit(() -> {
            assertTrue(start.await(3, TimeUnit.SECONDS));
            for (int i = 0; i < count; i++) {
                registry.broadcast(EventMessage.now("TEST_EVENT", Map.of("number", Integer.toString(i))));
            }
            return null;
        });
        start.countDown();
        for (int i = 0; i < count; i++) {
            output.println(MessageCodec.encode(new Request("ping-" + i, "PING", Map.of())));
        }

        List<String> responseIds = new ArrayList<>();
        List<String> eventNumbers = new ArrayList<>();
        for (int i = 0; i < count * 2; i++) {
            Message message = received.poll(3, TimeUnit.SECONDS);
            assertTrue(message != null, "reader should receive every response and event");
            if (message instanceof Response response) {
                assertTrue(response.success());
                responseIds.add(response.requestId());
            } else if (message instanceof EventMessage event) {
                assertEquals("TEST_EVENT", event.eventType());
                eventNumbers.add(event.details().get("number"));
            }
        }
        broadcasts.get(3, TimeUnit.SECONDS);
        for (int i = 0; i < count; i++) {
            assertEquals(1, Collections.frequency(responseIds, "ping-" + i));
            assertEquals(1, Collections.frequency(eventNumbers, Integer.toString(i)));
        }
        assertTrue(errors.isEmpty());

        output.println(MessageCodec.encode(new Request("quit", "QUIT", Map.of())));
        assertEquals("quit", ((Response) received.poll(3, TimeUnit.SECONDS)).requestId());
        listenerTask.get(3, TimeUnit.SECONDS);
        assertEquals("server connection closed", errors.poll());
    }

    private Response readResponse(BufferedReader input) throws IOException {
        for (int i = 0; i < 100; i++) {
            Message message = MessageCodec.decode(input.readLine());
            if (message instanceof Response response) {
                return response;
            }
        }
        throw new AssertionError("no response received");
    }

    private EventMessage readEvent(BufferedReader input) throws IOException {
        return (EventMessage) MessageCodec.decode(input.readLine());
    }
}
