package client;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import protocol.Request;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class CommandParserTest {
    @Test
    void acceptsCommandsAndNamedArguments() {
        Request request = CommandParser.parse("  load_cargo transport=t1 amount=20  ");
        assertEquals("LOAD_CARGO", request.command());
        assertEquals(Map.of("transport", "t1", "amount", "20"), request.arguments());
        assertNotEquals(request.requestId(), CommandParser.parse("ping").requestId());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "  ", "ping extra", "ping =value", "ping name=", "ping a=1 a=2"})
    void rejectsBadInput(String line) {
        assertThrows(IllegalArgumentException.class, () -> CommandParser.parse(line));
    }
}
