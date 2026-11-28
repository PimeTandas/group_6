package server;

import service.*;
import persistance.PersistanceService;
import data.InitialDataLoader;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Scanner;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.nio.file.Path;

import static util.constants.*;

public class Server {

    private static final int PORT = 1234;
    private ServerSocket serverSocket;
    private volatile boolean running;
    private final ExecutorService pool = Executors.newCachedThreadPool();
    private final Set<Socket> connections = ConcurrentHashMap.newKeySet();
    private final ClientRegistry registry = new ClientRegistry();
    private final CommandDispatcher dispatcher;
    private final DepartureScheduler departures;
    private final SpaceportService spaceport;
    private final PersistanceService persistence;

    private static final Path SAVELOCATION = Path.of("src", "main", "resources", "data", "persistence");
    public Server() throws IOException {
        this(InitialDataLoader.load(SAVELOCATION), new PersistanceService(SAVELOCATION));
    }

    public Server(SpaceportService spaceport) {
        this(spaceport, null);
    }

    public Server(TransportRegistry transports) {
        this(transports, new EventLogService());
    }

    public Server(SpaceportService spaceport, PersistanceService persistence) {
        this.spaceport = spaceport;
        this.persistence = persistence;
        this.dispatcher = new CommandDispatcher(registry, spaceport);
        this.departures = spaceport.createDepartureScheduler(1000, registry::broadcast);
    }

    public Server(TransportRegistry transports, EventLogService eventLog) {
        this(new SpaceportService(transports, new CargoServiceImpl(), eventLog));
    }

    ClientLogService clientLogService = new ClientLogServiceImpl();

    public void startServer() throws IOException {
        System.out.println(YELLOW + "Starting Space Port Server...");
        try {
            serverSocket = new ServerSocket(PORT);
            departures.start();
            running = true;
            System.out.println("Space Port Server Started..." + DEFAULT);

            startConsoleListener();

            while (running) {
                try {
                    Socket socket = serverSocket.accept();
                    // track sockets before the handshake, so unfinished connections are closed too
                    connections.add(socket);
                    pool.submit(() -> {
                        try {
                            new ClientHandler(socket, clientLogService, registry, dispatcher).run();
                        } finally {
                            connections.remove(socket);
                        }
                    });
                } catch (IOException e) {
                    if (running) {
                        throw e;
                    }
                }
            }
        } finally {
            departures.stop();
            shutdown();
            // interrupting a handler alone does not unblock a socket read
            for (Socket socket : connections) {
                try {
                    socket.close();
                } catch (IOException e) {
                    System.err.println("could not close client socket: " + e.getMessage());
                }
            }
            pool.shutdown();
            try {
                if (!pool.awaitTermination(5, TimeUnit.SECONDS)) {
                    pool.shutdownNow();
                }
            } catch (InterruptedException e) {
                pool.shutdownNow();
                Thread.currentThread().interrupt();
            }
            if (persistence != null && serverSocket != null) {
                saveState();
            }
        }
    }

    // Starts Space Port Server on PORT: 1234
    public static void main(String[] args) throws IOException {
        Server server = new Server();
        server.startServer();
    }

    // Console listening thread so we can still quit while server is doing work
    private void startConsoleListener() {
        Thread console = new Thread(() -> {
            Scanner sc = new Scanner(System.in);
            while (sc.hasNextLine()) {
                if (sc.nextLine().trim().equalsIgnoreCase("QUIT")) {
                    shutdown();
                    break;
                }
            }
        });
        console.setDaemon(true);
        console.start();
    }

    private void saveState() {
        try {
            persistence.save(spaceport);
            System.out.println(YELLOW + "Saved data to " + persistence.getDir().toAbsolutePath() + DEFAULT);
        } catch (IOException e) {
            System.err.println("failed to save data: " + e.getMessage());
        }
    }

    private synchronized void shutdown() {
        if (!running) {
            return;
        }
        running = false;
        System.out.println(YELLOW + "Shutting down Space Port Server" + DEFAULT);
        try {
            serverSocket.close();
        }
        catch (IOException e) {
            System.err.println("could not close server socket: " + e.getMessage());
        }
    }
}
