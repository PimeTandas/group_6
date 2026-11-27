package service;

import enums.ClientType;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;

public class ClientLogServiceImpl implements ClientLogService {

    private static final String PATH = "src\\main\\resources\\data\\clients.log";
    private final Path logFile = Path.of(PATH);

    // Log client connection time
    public void logConnected(String clientName, ClientType clientType) {
        write(clientName + " connected as " + clientType);
    }

    // Log client disconnect time
    public void logDisconnected(String clientName, ClientType clientType) {
        write(clientName + " disconnected as " + clientType);
    }

    /***
     * Synchronized locks da method. All ClientHandler threads share the same ClientLogService so this prevents race conditions.
     *
     * @param message String to be written to the log file.
     */
    private synchronized void write(String message) {
        String line = "[" + LocalDateTime.now() + "] " + message + System.lineSeparator();
        try {
            Files.createDirectories(logFile.getParent());
            Files.writeString(logFile, line, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            System.err.println("Failed to update log: " + e.getMessage());
        }
    }
}
