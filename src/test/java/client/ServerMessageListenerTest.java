package client;

import org.junit.jupiter.api.Test;
import protocol.*;
import java.io.BufferedReader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class ServerMessageListenerTest {
    @Test
    void readsEventsAndResponsesWithoutStoppingOnBadMessages() {
        EventMessage event = EventMessage.now("CLIENT_CONNECTED", Map.of("client", "other"));
        Response response = new Response("r1", true, "pong", List.of());
        String lines = MessageCodec.encode(event) + "\ninvalid\n"
                + MessageCodec.encode(new Request("r2", "PING", Map.of())) + "\n"
                + MessageCodec.encode(response) + "\n";
        List<Message> received = new ArrayList<>();
        List<String> errors = new ArrayList<>();

        new ServerMessageListener(new BufferedReader(new StringReader(lines)), received::add, errors::add).run();

        assertEquals(List.of(event, response), received);
        assertEquals(List.of("invalid message from server", "invalid message from server", "server connection closed"), errors);
    }
}
