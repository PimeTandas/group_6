package protocol;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class MessageCodecTest {
    @Test
    void preservesRequestArgumentsAndEscapedText() {
        Request request = new Request("r1", "PING", Map.of("text", "hello\n\"port\" £"));
        String encoded = MessageCodec.encode(request);
        assertFalse(encoded.contains("\n"));
        assertEquals(request, MessageCodec.decode(encoded));
    }

    @Test
    void preservesResponseAndEvent() {
        Response response = new Response("r2", true, "pong", List.of("terminal-1"));
        EventMessage event = EventMessage.now("CLIENT_CONNECTED", Map.of("client", "terminal-1"));
        assertEquals(response, MessageCodec.decode(MessageCodec.encode(response)));
        assertEquals(event, MessageCodec.decode(MessageCodec.encode(event)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "not json", "null", "[]", "{}",
            "{\"type\":\"other\",\"payload\":{}}",
            "{\"type\":\"request\",\"payload\":{}}"})
    void rejectsMalformedMessages(String line) {
        assertThrows(IllegalArgumentException.class, () -> MessageCodec.decode(line));
    }
}
