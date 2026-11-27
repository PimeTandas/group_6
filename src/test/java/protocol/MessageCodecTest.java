package protocol;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class MessageCodecTest {
    @Test
    void roundTripsAllMessageTypesWithoutSplittingLines() {
        Request request = new Request("r1", "PING", Map.of("text", "hello\n\"port\" £"));
        Response response = new Response("r2", true, "pong", List.of("terminal-1"));
        EventMessage event = EventMessage.now("CLIENT_CONNECTED", Map.of("client", "terminal-1"));
        for (Message message : List.of(request, response, event)) {
            String encoded = MessageCodec.encode(message);
            assertAll(message.getClass().getSimpleName(),
                    () -> assertFalse(encoded.contains("\n")),
                    () -> assertEquals(message, MessageCodec.decode(encoded)));
        }
    }

    @Test
    void rejectsMalformedAndIncompleteMessages() {
        for (String line : List.of("", "not json", "null", "[]", "{}",
                "{\"type\":\"other\",\"payload\":{}}",
                "{\"type\":\"request\",\"payload\":{}}")) {
            assertThrows(IllegalArgumentException.class, () -> MessageCodec.decode(line), "input: " + line);
        }
    }
}
