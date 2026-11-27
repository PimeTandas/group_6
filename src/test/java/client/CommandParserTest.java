package client;

import org.junit.jupiter.api.Test;
import protocol.Request;
import java.util.Map;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class CommandParserTest {
    @Test
    void acceptsCommandsAndNamedArguments() {
        Request request = CommandParser.parse("  load_cargo transport=t1 amount=20  ");
        assertEquals("LOAD_CARGO", request.command());
        assertEquals(Map.of("transport", "t1", "amount", "20"), request.arguments());
        assertNotEquals(request.requestId(), CommandParser.parse("ping").requestId());
    }

    @Test
    void rejectsBlankCommandsAndMalformedArguments() {
        for (String line : List.of("", "  ", "ping extra", "ping =value", "ping name=", "ping a=1 a=2")) {
            assertThrows(IllegalArgumentException.class, () -> CommandParser.parse(line), "input: " + line);
        }
    }
}
