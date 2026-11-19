package protocol;

import java.util.Map;

public record Request(String requestId, String command, Map<String, String> arguments) implements Message {
    public Request {
        if (requestId == null || requestId.isBlank() || command == null || command.isBlank()) {
            throw new IllegalArgumentException("request id and command are required");
        }
        arguments = Map.copyOf(arguments);
    }
}
