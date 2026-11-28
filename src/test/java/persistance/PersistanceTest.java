package persistance;

import data.InitialDataLoader;
import enums.BayStatus;
import enums.MaintenanceStatus;
import enums.TransportState;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import service.SpaceportService;
import service.SpaceportService.TransportView;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class PersistanceTest {

    @TempDir
    Path dir;

    @Test
    void usesSuppliedDataWhenNothingSavedYet() throws Exception {
        SpaceportService spaceport = InitialDataLoader.load(dir);

        assertEquals(10, spaceport.getTransports().size());
        assertEquals(8, spaceport.getBays().size());
        assertEquals(4, spaceport.getTeams().size());
        assertTrue(spaceport.getEvents().isEmpty());
    }

    @Test
    void saveWritesAllFiles() throws Exception {
        new PersistanceService(dir).save(InitialDataLoader.load(dir));

        assertTrue(Files.exists(dir.resolve("transports.csv")));
        assertTrue(Files.exists(dir.resolve("docking.csv")));
        assertTrue(Files.exists(dir.resolve("maintenance.csv")));
        assertTrue(Files.exists(dir.resolve("events.log")));
    }

    @Test
    void savedStateRestoredCorrectl() throws Exception {
        SpaceportService before = InitialDataLoader.load(dir);

        before.completeMaintenance("PW-T005", "r1");
        before.allocateBay("PW-T001", "BAY-07", "r2");
        before.transferCargo("PW-T003", "PW-T001", 10, "r3");
        before.addTransport("PW-T099", 300, LocalDateTime.of(2027, 3, 1, 9, 30), "r4");
        before.reopenBay("BAY-08", "r5");

        new PersistanceService(dir).save(before);
        SpaceportService after = InitialDataLoader.load(dir);

        assertEquals(before.getTransports(), after.getTransports());
        assertEquals(before.getBays(), after.getBays());
        assertEquals(before.getTeams(), after.getTeams());
        assertEquals(before.getEvents(), after.getEvents());
        assertEquals(5, after.getEvents().size());
    }

    @Test
    void restoredCommandsStillWork() throws Exception {
        SpaceportService before = InitialDataLoader.load(dir);
        before.completeMaintenance("PW-T005", "r1");
        new PersistanceService(dir).save(before);

        SpaceportService after = InitialDataLoader.load(dir);

        after.assignTeam("PW-T003", "TEAM-01", "r2");
        assertEquals(TransportState.SERVICING, find(after, "PW-T003").state());
        assertEquals("PW-T003", after.getTeams().get(0).transportId());

        after.exchangeBays("PW-T005", "PW-T007", "r3");
        assertEquals("BAY-05", find(after, "PW-T005").bayId());
        assertEquals("PW-T005", after.getBays().get(4).transportId());
        assertEquals("PW-T007", after.getBays().get(2).transportId());
    }

    @Test
    void departedTransportsStayDepartedAndFreeResourcesStayFree() throws Exception {
        new PersistanceService(dir).save(InitialDataLoader.load(dir));

        SpaceportService after = InitialDataLoader.load(dir);
        assertEquals(TransportState.DEPARTED, find(after, "PW-T009").state());
        assertNull(find(after, "PW-T009").bayId());
        assertEquals(BayStatus.AVAILABLE, after.getBays().get(6).status());
        assertEquals(BayStatus.CLOSED, after.getBays().get(7).status());
        assertEquals(MaintenanceStatus.AVAILABLE, after.getTeams().get(3).status());
    }

    @Test
    void savingTwiceKeepsOnlyTheLatestState() throws Exception {
        SpaceportService spaceport = InitialDataLoader.load(dir);
        PersistanceService persistence = new PersistanceService(dir);
        persistence.save(spaceport);
        spaceport.loadCargo("PW-T003", 20, "r1");
        persistence.save(spaceport);

        SpaceportService after = InitialDataLoader.load(dir);
        assertEquals(120, find(after, "PW-T003").quantity());
        assertEquals(1, after.getEvents().size());
        try (var files = Files.list(dir)) {
            assertTrue(files.noneMatch(file -> file.toString().endsWith(".tmp")));
        }
    }

    @Test
    void transportIdsWithCommasAreRejected() throws Exception {
        SpaceportService spaceport = InitialDataLoader.load(dir);

        assertThrows(IllegalArgumentException.class,
                () -> spaceport.addTransport("A,B", 10, LocalDateTime.of(2027, 1, 1, 10, 0), "r1"));
        assertEquals(10, spaceport.getTransports().size());
    }

    private static TransportView find(SpaceportService spaceport, String id) {
        return spaceport.getTransports().stream().filter(t -> t.id().equals(id)).findFirst().orElseThrow();
    }
}