package domains;

import enums.TransportState;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertAll;

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

    @Test
    void rejectsSkippedBackwardAndPostDepartureTransitions() {
        TransportState[][] invalid = {
                {TransportState.WAITING, TransportState.READY},
                {TransportState.DOCKED, TransportState.DEPARTED},
                {TransportState.SERVICING, TransportState.DEPARTED},
                {TransportState.READY, TransportState.DOCKED},
                {TransportState.DEPARTED, TransportState.WAITING}
        };
        // representative failures cover skipping stages, going backwards and restarting after departure
        for (TransportState[] transition : invalid) {
            TransportState start = transition[0];
            TransportState next = transition[1];
            Transporter transport = new Transporter("t1", start,
                    null, 100, 0, LocalDateTime.of(2026, 11, 30, 12, 0), null);
            assertAll(start + " -> " + next,
                    () -> assertThrows(IllegalStateException.class, () -> transport.setState(next)),
                    () -> assertEquals(start, transport.getState()));
        }
    }

    @Test
    void rejectsRepeatedAndMissingStates() {
        for (TransportState state : TransportState.values()) {
            Transporter transport = new Transporter("t1", state,
                    null, 100, 0, LocalDateTime.of(2026, 11, 30, 12, 0), null);
            assertAll(state.toString(),
                    () -> assertThrows(IllegalStateException.class, () -> transport.setState(state)),
                    () -> assertThrows(IllegalStateException.class, () -> transport.setState(null)),
                    () -> assertEquals(state, transport.getState()));
        }
    }
}
