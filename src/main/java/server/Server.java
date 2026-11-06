package server;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Server {

    private static final int PORT = 1234;
    private ServerSocket serverSocket;
    private final ExecutorService pool = Executors.newFixedThreadPool(10);

    public void startServer() throws IOException {
        System.out.println("Starting Space Port Server...");
        serverSocket = new ServerSocket(PORT);
        System.out.println("Space Port Server Started...");

        while(true) {
            Socket socket = serverSocket.accept();
            pool.submit(new ClientHandler(socket));
        }
    }


    /***
     * Starts Space Port Server on PORT: 1234
     */
    public static void main(String[] args) throws IOException {
        Server server = new Server();
        server.startServer();
    }

}
