package server;

import service.ClientLogService;
import service.ClientLogServiceImpl;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Scanner;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static util.constants.*;

public class Server {

    private static final int PORT = 1234;
    private ServerSocket serverSocket;
    private final ExecutorService pool = Executors.newCachedThreadPool();

    // Services can go here, should make sure all use synchronized methods!!!!
    ClientLogService clientLogService = new ClientLogServiceImpl();

    public void startServer() throws IOException {
        System.out.println(YELLOW + "Starting Space Port Server...");
        serverSocket = new ServerSocket(PORT);
        System.out.println("Space Port Server Started..." + DEFAULT);

        startConsoleListener();

        while(true) {
            try {
                Socket socket = serverSocket.accept();
                pool.submit(new ClientHandler(socket, clientLogService));
            } catch (IOException e) {
                System.out.println(RED + "Failed to handle Client connection" + DEFAULT);
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

     // Handle shut down of Space Port Server - closes socket and thread pool
    private void shutdown() {
        System.out.println(YELLOW + "Shutting down Space Port Server" + DEFAULT);
        try {
            serverSocket.close();
            pool.shutdownNow();
            System.exit(0);
        }
        catch (IOException e) {
            e.printStackTrace();

        }
    }
}