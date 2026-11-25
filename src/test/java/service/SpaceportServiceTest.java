package service;

import domains.DockingBay;
import domains.MaintenanceTeam;
import domains.Transporter;
import enums.BayStatus;
import enums.MaintenanceStatus;
import enums.TransportState;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import server.TransportRegistry;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@Timeout(10)
class SpaceportServiceTest {
    private final TransportRegistry transports = new TransportRegistry();
    private final EventLogService log = new EventLogService();
    private final SpaceportService spaceport = new SpaceportService(transports, new CargoServiceImpl(), log);
    private final LocalDateTime departure = LocalDateTime.of(2026, 11, 30, 17, 0);

    private Transporter register(String id, int capacity, int quantity) {
        Transporter transport = new Transporter(id, TransportState.DOCKED, null, capacity, quantity, departure, null);
        transports.register(transport);
        return transport;
    }

    @Test
    void validatesNewTransportsAndLogsOnlySuccessfulAdditions() {
        spaceport.addTransport("t1", 100, departure, "add");
        assertEquals(TransportState.WAITING, transports.get("t1").getState());
        assertEquals(0, transports.get("t1").getCurrentQuantity());
        assertThrows(IllegalArgumentException.class, () -> spaceport.addTransport("t1", 100, departure, "duplicate"));
        assertThrows(IllegalArgumentException.class, () -> spaceport.addTransport(" ", 100, departure, "blank"));
        assertThrows(IllegalArgumentException.class, () -> spaceport.addTransport("bad", -1, departure, "capacity"));
        assertThrows(IllegalArgumentException.class, () -> spaceport.addTransport("bad", 100, null, "time"));
        assertEquals(1, spaceport.getEvents().size());
        assertEquals("ADD_TRANSPORT", spaceport.getEvents().get(0).eventType());
    }

    @Test
    void viewsKeepTheirValuesAfterTheUnderlyingTransportChanges() {
        register("t1", 100, 20);
        var before = spaceport.getTransports();
        var eventsBefore = spaceport.getEvents();
        spaceport.loadCargo("t1", 5, "load");
        assertEquals(20, before.get(0).quantity());
        assertEquals(25, spaceport.getTransports().get(0).quantity());
        assertTrue(eventsBefore.isEmpty());
        assertThrows(UnsupportedOperationException.class, before::clear);
    }

    @Test
    void resourceViewsAreSortedAndDoNotExposeMutableResources() {
        DockingBay bay = new DockingBay("b2", BayStatus.AVAILABLE, null);
        SpaceportService resources = new SpaceportService(transports, new CargoServiceImpl(), log,
                List.of(bay, new DockingBay("b1", BayStatus.CLOSED, null)),
                List.of(new MaintenanceTeam("m1", MaintenanceStatus.AVAILABLE, null)));
        var before = resources.getBays();
        synchronized (bay) {
            bay.setStatus(BayStatus.CLOSED);
        }
        assertEquals(List.of("b1", "b2"), before.stream().map(SpaceportService.BayView::id).toList());
        assertEquals(BayStatus.AVAILABLE, before.get(1).status());
        assertEquals(BayStatus.CLOSED, resources.getBays().get(1).status());
        assertEquals("m1", resources.getTeams().get(0).id());
        assertThrows(IllegalArgumentException.class, () -> new SpaceportService(transports, new CargoServiceImpl(), log,
                List.of(bay, bay), List.of()));
    }

    @Test
    void rejectsInvalidTransfersWithoutChangingCargoOrLog() {
        register("a", 100, 20);
        register("b", 100, 95);
        assertThrows(IllegalArgumentException.class, () -> spaceport.transferCargo("a", "b", 6, "full"));
        assertThrows(IllegalArgumentException.class, () -> spaceport.transferCargo("a", "a", 1, "same"));
        assertThrows(IllegalArgumentException.class, () -> spaceport.transferCargo("a", "missing", 1, "missing"));
        assertEquals(List.of(20, 95), spaceport.getTransports().stream().map(SpaceportService.TransportView::quantity).toList());
        assertTrue(spaceport.getEvents().isEmpty());
    }

    @Test
    void concurrentLoadsRespectCapacityAndLogEverySuccessOnce() throws Exception {
        register("a", 40, 0);
        AtomicInteger successful = new AtomicInteger();
        runConcurrently(() -> {
            for (int i = 0; i < 30; i++) {
                try {
                    spaceport.loadCargo("a", 1, Thread.currentThread().getName() + i);
                    successful.incrementAndGet();
                } catch (IllegalArgumentException expectedWhenFull) {
                    assertEquals("Not enough cargo capacity available.", expectedWhenFull.getMessage());
                }
            }
        }, () -> {
            for (int i = 0; i < 30; i++) {
                try {
                    spaceport.loadCargo("a", 1, Thread.currentThread().getName() + i);
                    successful.incrementAndGet();
                } catch (IllegalArgumentException expectedWhenFull) {
                    assertEquals("Not enough cargo capacity available.", expectedWhenFull.getMessage());
                }
            }
        });
        assertEquals(40, successful.get());
        assertEquals(40, transports.get("a").getCurrentQuantity());
        assertEquals(40, spaceport.getEvents().size());
        assertEquals(40, spaceport.getEvents().stream().map(event -> event.details().get("requestId")).distinct().count());
    }

    @Test
    void opposingTransfersFinishWithoutDeadlockAndConserveCargo() throws Exception {
        register("a", 1000, 500);
        register("b", 1000, 500);
        runConcurrently(() -> {
            for (int i = 0; i < 200; i++) spaceport.transferCargo("a", "b", 1, "forward-" + i);
        }, () -> {
            for (int i = 0; i < 200; i++) spaceport.transferCargo("b", "a", 1, "reverse-" + i);
        });
        assertEquals(List.of(500, 500), spaceport.getTransports().stream().map(SpaceportService.TransportView::quantity).toList());
        assertEquals(400, spaceport.getEvents().size());
    }

    @Test
    void operationsOnAnotherTransportCanFinishWhileOneIsLocked() throws Exception {
        Transporter blocked = register("a", 100, 0);
        register("b", 100, 0);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch attempted = new CountDownLatch(1);
        try {
            Future<?> waiting;
            synchronized (blocked) {
                waiting = pool.submit(() -> {
                    attempted.countDown();
                    spaceport.loadCargo("a", 1, "blocked");
                });
                assertTrue(attempted.await(2, TimeUnit.SECONDS));
                pool.submit(() -> spaceport.loadCargo("b", 1, "independent")).get(2, TimeUnit.SECONDS);
                assertFalse(waiting.isDone());
                assertEquals(1, transports.get("b").getCurrentQuantity());
            }
            waiting.get(2, TimeUnit.SECONDS);
        } finally {
            pool.shutdownNow();
            assertTrue(pool.awaitTermination(2, TimeUnit.SECONDS));
        }
    }

    @Test
    void concurrentDuplicateAdditionsProduceOneTransportAndOneEvent() throws Exception {
        AtomicInteger successful = new AtomicInteger();
        Runnable add = () -> {
            try {
                spaceport.addTransport("same", 100, departure, "add");
                successful.incrementAndGet();
            } catch (IllegalArgumentException duplicate) {
                assertTrue(duplicate.getMessage().contains("already exists"));
            }
        };
        runConcurrently(add, add);
        assertEquals(1, successful.get());
        assertEquals(1, spaceport.getTransports().size());
        assertEquals(1, spaceport.getEvents().size());
    }

    private void runConcurrently(Runnable... actions) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(actions.length);
        CountDownLatch ready = new CountDownLatch(actions.length);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> results = new ArrayList<>();
        try {
            for (Runnable action : actions) {
                results.add(pool.submit(() -> {
                    ready.countDown();
                    try {
                        assertTrue(start.await(2, TimeUnit.SECONDS));
                        action.run();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new AssertionError(e);
                    }
                }));
            }
            assertTrue(ready.await(2, TimeUnit.SECONDS));
            start.countDown();
            for (Future<?> result : results) result.get(5, TimeUnit.SECONDS);
        } finally {
            start.countDown();
            pool.shutdownNow();
            assertTrue(pool.awaitTermination(2, TimeUnit.SECONDS));
        }
    }
}
