package server;

import enums.ClientType;
import protocol.*;
import service.ClientLogService;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class ClientHandler implements Runnable {
    private final Socket socket;
    private final ClientLogService clientLogService;
    private final ClientRegistry registry;
    private final CommandDispatcher dispatcher;
    private final Object sendLock = new Object();
    private ClientMessageWriter writer;
    private String clientName;
    private ClientType clientType;

    public ClientHandler(Socket socket, ClientLogService clientLogService) {
        this(socket, clientLogService, new ClientRegistry());
    }

    public ClientHandler(Socket socket, ClientLogService clientLogService, ClientRegistry registry) {
        this(socket, clientLogService, registry, new CommandDispatcher(registry));
    }

    public ClientHandler(Socket socket, ClientLogService clientLogService, ClientRegistry registry,
                         CommandDispatcher dispatcher) {
        this.socket = socket;
        this.clientLogService = clientLogService;
        this.registry = registry;
        this.dispatcher = dispatcher;
    }

    @Override
    public void run() {
        boolean registered = false;
        try {
            BufferedReader input = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            synchronized (sendLock) {
                writer = new ClientMessageWriter(socket);
            }
            // keep the existing two-line handshake while commands use the shared protocol
            socket.setSoTimeout(10000);
            String name = input.readLine();
            String role = input.readLine();
            if (name == null || name.isBlank() || role == null) {
                send(new Response("", false, "client name and role are required", List.of()));
                return;
            }
            clientName = name.trim();
            try {
                clientType = ClientType.valueOf(role.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                send(new Response("", false, "invalid client role", List.of()));
                return;
            }
            // registration makes us visible to broadcasts, so send the welcome before releasing the writer
            synchronized (sendLock) {
                registered = registry.register(clientName, this);
                if (registered) {
                    send(new Response("", true, "connected as " + clientType, List.of()));
                }
            }
            if (!registered) {
                send(new Response("", false, "client name is already connected", List.of()));
                return;
            }
            socket.setSoTimeout(0);
            clientLogService.logConnected(clientName, clientType);
            registry.broadcast(EventMessage.now("CLIENT_CONNECTED", Map.of("client", clientName)));

            String line;
            while ((line = input.readLine()) != null) {
                // older terminals still send a plain quit line
                if (line.equalsIgnoreCase("QUIT")) {
                    break;
                }
                Request request;
                try {
                    Message message = MessageCodec.decode(line);
                    if (!(message instanceof Request)) {
                        throw new IllegalArgumentException("expected a request");
                    }
                    request = (Request) message;
                } catch (IllegalArgumentException e) {
                    send(new Response("", false, "invalid request", List.of()));
                    continue;
                }
                Response response = dispatcher.dispatch(request, clientType);
                send(response);
                if (response.success() && request.command().equalsIgnoreCase("QUIT")) {
                    break;
                }
            }
        } catch (IOException e) {
            if (!socket.isClosed()) {
                System.err.println("client connection ended: " + e.getMessage());
            }
        } finally {
            try {
                if (registered) {
                    registry.remove(clientName, this);
                    clientLogService.logDisconnected(clientName, clientType);
                    registry.broadcast(EventMessage.now("CLIENT_DISCONNECTED", Map.of("client", clientName)));
                }
            } finally {
                ClientMessageWriter outgoing;
                synchronized (sendLock) {
                    outgoing = writer;
                }
                if (outgoing != null) {
                    outgoing.finish();
                } else {
                    close();
                }
            }
        }
    }

    public void send(Message message) {
        // welcome is queued before registration allows any broadcasts to this handler
        synchronized (sendLock) {
            if (writer != null) {
                writer.send(message);
            }
        }
    }

    public void close() {
        try {
            socket.close();
        } catch (IOException e) {
            System.err.println("could not close client socket: " + e.getMessage());
        }
    }
}
