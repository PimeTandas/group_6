package domains;

import enums.TransportState;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TransporterTest {

    @Test
    void followsDepartureSequence() {
        Transporter transport = new Transporter("t1", TransportState.WAITING,
                null, 100, 0, LocalDateTime.of(2026, 11, 30, 12, 0), null);

        transport.setState(TransportState.DOCKED);
        assertEquals(TransportState.DOCKED, transport.getState());

        transport.setState(TransportState.SERVICING);
        assertEquals(TransportState.SERVICING, transport.getState());

        transport.setState(TransportState.READY);
        assertEquals(TransportState.READY, transport.getState());

        transport.setState(TransportState.DEPARTED);
        assertEquals(TransportState.DEPARTED, transport.getState());
    }

    @ParameterizedTest
    @CsvSource({
            "WAITING, SERVICING",
            "WAITING, READY",
            "WAITING, DEPARTED",
            "DOCKED, READY",
            "DOCKED, DEPARTED",
            "SERVICING, DEPARTED",
            "DOCKED, WAITING",
            "SERVICING, WAITING",
            "SERVICING, DOCKED",
            "READY, WAITING",
            "READY, DOCKED",
            "READY, SERVICING",
            "DEPARTED, WAITING",
            "DEPARTED, DOCKED",
            "DEPARTED, SERVICING",
            "DEPARTED, READY"
    })
    void rejectsInvalidTransitionWithoutChangingState(TransportState start, TransportState next) {
        Transporter transport = new Transporter("t1", start,
                null, 100, 0, LocalDateTime.of(2026, 11, 30, 12, 0), null);

        assertThrows(IllegalStateException.class, () -> transport.setState(next));
        assertEquals(start, transport.getState());
    }

    @ParameterizedTest
    @EnumSource(TransportState.class)
    void rejectsRepeatedState(TransportState state) {
        Transporter transport = new Transporter("t1", state,
                null, 100, 0, LocalDateTime.of(2026, 11, 30, 12, 0), null);

        assertThrows(IllegalStateException.class, () -> transport.setState(state));
        assertEquals(state, transport.getState());
    }

    @ParameterizedTest
    @EnumSource(TransportState.class)
    void rejectsNullState(TransportState state) {
        Transporter transport = new Transporter("t1", state,
                null, 100, 0, LocalDateTime.of(2026, 11, 30, 12, 0), null);

        assertThrows(IllegalStateException.class, () -> transport.setState(null));
        assertEquals(state, transport.getState());
    }
}
