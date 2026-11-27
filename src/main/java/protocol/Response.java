package protocol;

import java.util.List;

public record Response(String requestId, boolean success, String message, List<String> data) implements Message {
    public Response {
        if (requestId == null || message == null) {
            throw new IllegalArgumentException("response id and message are required");
        }
        data = List.copyOf(data);
    }
}
