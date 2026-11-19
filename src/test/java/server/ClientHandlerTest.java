package server;

import enums.ClientType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import service.ClientLogService;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
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
        sockets.add(client);
        return client;
    }

    private Future<?> startHandler() throws IOException {
        Socket accepted = listener.accept();
        sockets.add(accepted);
        return handlers.submit(new ClientHandler(accepted, log));
    }
}
