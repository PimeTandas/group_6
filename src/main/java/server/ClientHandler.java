package server;

import enums.ClientType;
import service.ClientLogService;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

import static util.constants.*;

public class ClientHandler implements Runnable {

    private Socket socket;
    private ClientLogService clientLogService;
    private String clientName;
    private ClientType clientType;

    public ClientHandler(Socket socket, ClientLogService clientLogService) {
        this.socket = socket;
        this.clientLogService = clientLogService;
    }

    @Override
    public void run() {
        System.out.println(GREEN + "Thread handling Client connecting to port Space Port Server" + DEFAULT);
        try {
            // This is the output stream of the CLIENT (INPUT TO SERVER)
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            // This is how we write back to the Client (OUTPUT TO CLIENT)
            PrintWriter out = new PrintWriter(socket.getOutputStream(), true);

            this.clientName = in.readLine();
            this.clientType = ClientType.valueOf(in.readLine());
            clientLogService.logConnected(clientName, clientType);
            System.out.println(GREEN + clientName + " connected to Space Port Server, as a " + clientType + DEFAULT);

            String line;
            while((line = in.readLine()) != null) {
                if (line.contains("QUIT")){
                    break;
                }
            }
            System.out.println(RED + clientName + " disconnected" + DEFAULT);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }finally {
            clientLogService.logDisconnected(clientName, clientType);
        }

    }
}
