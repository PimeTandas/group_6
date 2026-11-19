package protocol;

import java.time.Instant;
import java.util.Map;

public record EventMessage(String timestamp, String eventType, Map<String, String> details) implements Message {
    public EventMessage {
        Instant.parse(timestamp);
        if (eventType == null || eventType.isBlank()) {
            throw new IllegalArgumentException("event type is required");
        }
        details = Map.copyOf(details);
    }

    public static EventMessage now(String eventType, Map<String, String> details) {
        return new EventMessage(Instant.now().toString(), eventType, details);
    }
}
