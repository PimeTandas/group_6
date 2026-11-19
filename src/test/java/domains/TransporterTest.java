package domains;

import enums.TransportState;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
}
