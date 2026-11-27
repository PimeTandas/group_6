package data;

import enums.BayStatus;
import enums.MaintenanceStatus;
import enums.TransportState;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class InitialDataLoaderTest {
    @Test
    void loadsInitialDataAndKeepsAllocationsUsableByTheServices() throws Exception {
        var spaceport = InitialDataLoader.load();
        assertEquals(List.of(TransportState.WAITING, TransportState.WAITING, TransportState.DOCKED, TransportState.DOCKED,
                        TransportState.SERVICING, TransportState.SERVICING, TransportState.READY, TransportState.READY,
                        TransportState.DEPARTED, TransportState.DEPARTED),
                spaceport.getTransports().stream().map(transport -> transport.state()).toList());
        assertEquals(8, spaceport.getBays().size());
        assertEquals(4, spaceport.getTeams().size());
        assertEquals("PW-T003", spaceport.getBays().get(0).transportId());
        assertEquals("PW-T005", spaceport.getBays().get(2).transportId());
        assertEquals("PW-T008", spaceport.getBays().get(5).transportId());
        assertEquals(BayStatus.AVAILABLE, spaceport.getBays().get(6).status());
        assertEquals(BayStatus.CLOSED, spaceport.getBays().get(7).status());
        assertEquals("PW-T005", spaceport.getTeams().get(0).transportId());
        assertEquals(MaintenanceStatus.BUSY, spaceport.getTeams().get(0).status());
        assertEquals(MaintenanceStatus.AVAILABLE, spaceport.getTeams().get(2).status());
        assertTrue(spaceport.getEvents().isEmpty());

        spaceport.completeMaintenance("PW-T005", "complete");
        assertEquals(TransportState.READY, spaceport.getTransports().get(4).state());
        assertNull(spaceport.getTransports().get(4).teamId());
        assertNull(spaceport.getTeams().get(0).transportId());
        assertEquals(MaintenanceStatus.AVAILABLE, spaceport.getTeams().get(0).status());
        spaceport.allocateBay("PW-T001", "BAY-07", "dock");
        assertEquals("PW-T001", spaceport.getBays().get(6).transportId());
        spaceport.transferCargo("PW-T003", "PW-T001", 10, "transfer");
        assertEquals(10, spaceport.getTransports().get(0).quantity());
        assertEquals(90, spaceport.getTransports().get(2).quantity());
        assertEquals(3, spaceport.getEvents().size());
    }
}
