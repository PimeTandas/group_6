package server;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

import static util.constants.DEFAULT;
import static util.constants.GREEN;

public class ClientHandler implements Runnable {

    private Socket socket;

    public ClientHandler(Socket socket) {
        this.socket = socket;
    }

    @Override
    public void run() {
        System.out.println(GREEN + "Thread handling Client connecting to port Space Port Server" + DEFAULT);
        try {
            // This is the output stream of the CLIENT (INPUT TO SERVER)
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            // This is how we write back to the Client (OUTPUT TO CLIENT)
            PrintWriter out = new PrintWriter(socket.getOutputStream(), true);

            System.out.println(GREEN + in.readLine() + " connected to Space Port Server, as a " + in.readLine() + DEFAULT);

            while(true) {

            }

        } catch (IOException e) {
            throw new RuntimeException(e);
        }

    }
}
