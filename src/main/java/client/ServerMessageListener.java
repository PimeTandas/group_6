package client;

import protocol.Message;
import protocol.MessageCodec;
import protocol.Request;
import java.io.BufferedReader;
import java.io.IOException;
import java.util.function.Consumer;

public class ServerMessageListener implements Runnable {
    private final BufferedReader input;
    private final Consumer<Message> onMessage;
    private final Consumer<String> onError;

    public ServerMessageListener(BufferedReader input, Consumer<Message> onMessage, Consumer<String> onError) {
        this.input = input;
        this.onMessage = onMessage;
        this.onError = onError;
    }

    @Override
    public void run() {
        // this is the only thread that reads the socket, the main thread reads keyboard input
        try (input) {
            String line;
            while ((line = input.readLine()) != null) {
                try {
                    Message message = MessageCodec.decode(line);
                    if (message instanceof Request) {
                        throw new IllegalArgumentException("server sent a request");
                    }
                    onMessage.accept(message);
                } catch (IllegalArgumentException e) {
                    onError.accept("invalid message from server");
                }
            }
            onError.accept("server connection closed");
        } catch (IOException e) {
            onError.accept("server connection closed: " + e.getMessage());
        }
    }
}
