package server;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

public class ClientHandler implements Runnable {

    private Socket socket;

    public ClientHandler(Socket socket) {
        this.socket = socket;
    }

    @Override
    public void run() {
        System.out.println("Thread handling Client connecting to port Space Port Server");
        try {
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            PrintWriter out = new PrintWriter(socket.getOutputStream(), true);

            System.out.println("Client connected to Space Port Server");
            String line;
            while ((line = in.readLine()) != null) {
                out.println("Server received: " + line);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

    }
}
