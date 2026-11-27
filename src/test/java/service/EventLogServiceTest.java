package service;

import org.junit.jupiter.api.Test;
import protocol.EventMessage;

import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

class EventLogServiceTest {
    private final EventLogService log = new EventLogService();

    @Test
    void returnsAnImmutableCopyOfTheLog() {
        EventMessage first = EventMessage.now("LOAD_CARGO", Map.of("transport", "a"));
        log.record(first);
        List<EventMessage> snapshot = log.getEvents();
        log.record(EventMessage.now("UNLOAD_CARGO", Map.of("transport", "a")));
        assertEquals(List.of(first), snapshot);
        assertThrows(UnsupportedOperationException.class, snapshot::clear);
        assertThrows(IllegalArgumentException.class, () -> log.record(null));
        assertEquals(2, log.getEvents().size());
    }

    @Test
    void readersCanTakeSnapshotsWhileOtherThreadsAppend() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(3);
        CountDownLatch ready = new CountDownLatch(3);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<?> first = pool.submit(() -> write("a", ready, start));
            Future<?> second = pool.submit(() -> write("b", ready, start));
            Future<?> reader = pool.submit(() -> {
                ready.countDown();
                assertTrue(start.await(3, TimeUnit.SECONDS));
                int previousSize = 0;
                for (int i = 0; i < 100; i++) {
                    List<EventMessage> snapshot = log.getEvents();
                    assertTrue(snapshot.size() >= previousSize);
                    assertTrue(snapshot.stream().allMatch(event -> event.details().containsKey("id")));
                    previousSize = snapshot.size();
                }
                return null;
            });
            assertTrue(ready.await(3, TimeUnit.SECONDS));
            start.countDown();
            first.get(3, TimeUnit.SECONDS);
            second.get(3, TimeUnit.SECONDS);
            reader.get(3, TimeUnit.SECONDS);
            assertEquals(200, log.getEvents().size());
            assertEquals(200, log.getEvents().stream().map(event -> event.details().get("id")).distinct().count());
        } finally {
            start.countDown();
            pool.shutdownNow();
            assertTrue(pool.awaitTermination(3, TimeUnit.SECONDS));
        }
    }

    private Void write(String name, CountDownLatch ready, CountDownLatch start) throws Exception {
        ready.countDown();
        assertTrue(start.await(3, TimeUnit.SECONDS));
        for (int i = 0; i < 100; i++) {
            log.record(EventMessage.now("TEST_EVENT", Map.of("id", name + i)));
        }
        return null;
    }
}
