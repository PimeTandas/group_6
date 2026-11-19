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
import java.util.List;
import java.util.concurrent.BlockingQueue;
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
