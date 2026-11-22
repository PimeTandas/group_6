package server;

import enums.ClientType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import protocol.EventMessage;
import protocol.Message;
import protocol.MessageCodec;
import protocol.Request;
import protocol.Response;
import service.ClientLogService;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClientServerIntegrationTest {

    private ServerSocket serverSocket;
    private ExecutorService pool;
    private final List<Socket> clients = new ArrayList<>();
    private final ClientRegistry registry = new ClientRegistry();

    private final ClientLogService log = new ClientLogService() {
        public void logConnected(String name, ClientType type) {
        }

        public void logDisconnected(String name, ClientType type) {
        }
    };

    @BeforeEach
    void startServer() throws IOException {
        serverSocket = new ServerSocket(0, 10, InetAddress.getLoopbackAddress());
        pool = Executors.newCachedThreadPool();
        pool.submit(() -> {
            while (!serverSocket.isClosed()) {
                try {
                    Socket socket = serverSocket.accept();
                    pool.submit(new ClientHandler(socket, log, registry));
                } catch (IOException e) {
                    if (!serverSocket.isClosed()) {
                        throw new RuntimeException(e);
                    }
                }
            }
        });
    }

    @AfterEach
    void stopServer() throws Exception {
        for (Socket client : clients) {
            client.close();
        }
        serverSocket.close();
        pool.shutdownNow();
        assertTrue(pool.awaitTermination(3, TimeUnit.SECONDS));
    }

    @Test
    void clientCanConnectAndPingServer() throws Exception {
        TestClient client = connect("terminal-one", "CLIENT");

        Response welcome = client.readResponse();
        assertTrue(welcome.success());

        client.readEvent();
        client.send(new Request("request-1", "PING", Map.of()));

        Response response = client.readResponse();
        assertTrue(response.success());
        assertEquals("request-1", response.requestId());
        assertEquals("pong", response.message());
    }

    @Test
    void adminCanListClientsAndSeeOneDisconnect() throws Exception {
        TestClient admin = connect("control", "ADMIN");
        assertTrue(admin.readResponse().success());
        admin.readEvent();

        TestClient operator = connect("operator", "CLIENT");
        assertTrue(operator.readResponse().success());

        EventMessage joined = admin.readEvent();
        assertEquals("CLIENT_CONNECTED", joined.eventType());
        assertEquals("operator", joined.details().get("client"));

        admin.send(new Request("list-1", "LIST_CLIENTS", Map.of()));
        assertEquals(List.of("control", "operator"), admin.readResponse().data());

        operator.output.println("QUIT");
        EventMessage left = admin.readEvent();
        assertEquals("CLIENT_DISCONNECTED", left.eventType());
        assertEquals("operator", left.details().get("client"));
    }

    private TestClient connect(String name, String role) throws IOException {
        Socket socket = new Socket(InetAddress.getLoopbackAddress(), serverSocket.getLocalPort());
        socket.setSoTimeout(3000);
        clients.add(socket);
        TestClient client = new TestClient(socket);
        client.output.println(name);
        client.output.println(role);
        return client;
    }

    private static class TestClient {
        private final BufferedReader input;
        private final PrintWriter output;

        TestClient(Socket socket) throws IOException {
            input = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            output = new PrintWriter(socket.getOutputStream(), true, StandardCharsets.UTF_8);
        }

        void send(Request request) {
            output.println(MessageCodec.encode(request));
        }

        Response readResponse() throws IOException {
            while (true) {
                Message message = MessageCodec.decode(input.readLine());
                if (message instanceof Response response) {
                    return response;
                }
            }
        }

        EventMessage readEvent() throws IOException {
            while (true) {
                Message message = MessageCodec.decode(input.readLine());
                if (message instanceof EventMessage event) {
                    return event;
                }
            }
        }
    }
}
