package client;

import enums.ClientType;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.Scanner;

public class Client {

    private static final String DEFAULT = "\u001B[0m";
    private static final String CYAN = "\u001B[36m";

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


        Socket socket = new Socket("localhost", 1234);

        System.out.printf("%s is connected as %s to %s:%d%n", clientName, clientType, socket.getInetAddress().getHostAddress(), socket.getPort());

        BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
        Scanner keyboard = new Scanner(System.in);

        new Thread(() -> {
                String line;
                while (true) {
                    try {
                        if (!((line = in.readLine()) != null)) break;
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                    System.out.println(line);
                }}).start();

        while (keyboard.hasNextLine()) {
            out.println(keyboard.nextLine());
        }
        socket.close();
    }

    /***
     * Method to print Client welcome screen.
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
     * Need to add some validation
     * @param scanner
     * @return String
     */
    private static String getClientName(Scanner scanner) {
        while (true) {
            System.out.print(CYAN + "Please enter a client name: " + DEFAULT);
            String name = scanner.nextLine().trim();

            return name;
        }
    }

    /***
     * Utility method to get the client type on client startup.
     * @param scanner
     * @return ClientType
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

}
