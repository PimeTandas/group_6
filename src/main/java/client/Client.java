package client;

import enums.ClientType;
import protocol.Message;
import protocol.MessageCodec;
import protocol.Request;
import protocol.Response;
import protocol.EventMessage;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;

import static util.constants.*;

public class Client {

    private String clientName;
    private ClientType clientType;

    private static Scanner scanner;

    Client() {
        scanner = new Scanner(System.in);
    }

    public static void main(String[] args) throws IOException {
        new Client().startClient();
    }

    private void startClient() throws IOException {
        printIntro();
        this.clientName = getClientName(scanner);
        this.clientType = getClientType(scanner);

        try (Socket socket = tryConnect()) {
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            PrintWriter out = new PrintWriter(socket.getOutputStream(), true, StandardCharsets.UTF_8);
            out.println(clientName);
            out.println(clientType);

            // wait for registration before accepting commands, a tcp connection alone isn't enough
            Message handshake = MessageCodec.decode(in.readLine());
            if (!(handshake instanceof Response response) || !response.success()) {
                displayMessage(handshake);
                return;
            }
            displayMessage(handshake);
            Thread listener = new Thread(new ServerMessageListener(in, Client::displayMessage, System.out::println), "server-listener");
            listener.setDaemon(true);
            listener.start();
            System.out.println("commands: ping, list_clients (admin), help, quit");

            while (!socket.isClosed() && scanner.hasNextLine()) {
                String line = scanner.nextLine().trim();
                if (line.equalsIgnoreCase("HELP")) {
                    System.out.println("commands: ping, list_clients (admin), help, quit");
                    continue;
                }
                try {
                    Request request = CommandParser.parse(line);
                    out.println(MessageCodec.encode(request));
                    if (out.checkError()) {
                        System.out.println("could not send command, connection closed");
                        break;
                    }
                    if (request.command().equals("QUIT") && request.arguments().isEmpty()) {
                        // allow the reader to display the goodbye response before closing the socket
                        socket.shutdownOutput();
                        listener.join(3000);
                        break;
                    }
                } catch (IllegalArgumentException e) {
                    System.out.println(e.getMessage());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        } catch (IllegalArgumentException e) {
            System.out.println("invalid handshake from server");
        }
    }

    private static void displayMessage(Message message) {
        if (message instanceof Response response) {
            String prefix = response.success() ? "ok" : "error";
            System.out.println(prefix + " [" + response.requestId() + "]: " + response.message()
                    + (response.data().isEmpty() ? "" : " " + response.data()));
        } else if (message instanceof EventMessage event) {
            System.out.println("event [" + event.timestamp() + "] " + event.eventType() + " " + event.details());
        }
    }

    /***
     * Utility function to print some funky reto ascii art
     */
    private static void printIntro(){
        System.out.println("""
         ____   ___  ____ _____  __        ___    _   _ ____  _____ ____
        |  _ \\ / _ \\|  _ \\_   _| \\ \\      / / \\  | \\ | |  _ \\| ____|  _ \\
        | |_) | | | | |_) || |    \\ \\ /\\ / / _ \\ |  \\| | | | |  _| | |_) |
        |  __/| |_| |  _ < | |     \\ V  V / ___ \\| |\\  | |_| | |___|  _ <
        |_|    \\___/|_| \\_\\|_|      \\_/\\_/_/   \\_\\_| \\_|____/|_____|_| \\_\\
        ====================================================================
                            SPACEPORT MANAGEMENT SYSTEM
        ====================================================================
        """);
    }

    /***
     * Utility function to get the name of the client from user on client startup.
     *
     * @param scanner
     * @return The client name the user is connecting to from the terminal
     */
    private static String getClientName(Scanner scanner) {
        while (true) {
            System.out.print(CYAN + "Please enter a client name: " + DEFAULT);
            String name = scanner.nextLine().trim();
            if (!name.isBlank()) {
                return name;
            }
            System.out.println("client name cannot be blank");
        }
    }

    /***
     * Utility method to get the client type on client startup.
     *
     * @param scanner
     * @return The client type the user is connecting as from the terminal
     */
    private static ClientType getClientType(Scanner scanner) {
        while (true) {
            System.out.print(CYAN + "Please enter a client type (\"Client\" or \"Admin\"): " + DEFAULT);
            String input = scanner.nextLine().trim().toUpperCase();

            try {
                return ClientType.valueOf(input);
            } catch (IllegalArgumentException e) {
                System.out.println("Client type should be: Client or Admin.");
            }
        }
    }

    /***
     * Utility function to get a valid port number.
     *
     * @param scanner
     * @return a validated port number for client to connect to on local host
     */
    private static int getPortNumber(Scanner scanner) {
        while (true) {
            System.out.print(CYAN + "Please enter a port number: " + DEFAULT);
            String port = scanner.nextLine().trim().toUpperCase();
            if (port.matches("\\d{1,5}") && Integer.parseInt(port) <= 65535) {
                return Integer.parseInt(port);
            }
            System.out.println("Invalid port, port must be 0-65535.");
        }
    }

    /**
     * Utility function to help with graceful connection failure and allow for attempted retries
     *
     * @return Socket connection to the server.
     */
    private Socket tryConnect() {
        while (true) {
            int port = getPortNumber(scanner);
            try {
                return new Socket("localhost", port);
            } catch (IOException e) {
                System.out.println(RED + "Could not connect to Space Port Server on port " + port + ". Is the server running?" + DEFAULT);
                System.out.println(RED + "Want to (R)try or (Q)uit?");

                switch (scanner.nextLine().trim().toUpperCase()) {
                    case "R":
                        continue;
                    case "Q":
                        System.exit(0);
                }
            }
        }
    }
}
