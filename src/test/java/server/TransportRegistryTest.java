package server;

import domains.Transporter;
import enums.TransportState;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class TransportRegistryTest {
    private final TransportRegistry registry = new TransportRegistry();

    private Transporter transport(String id) {
        return new Transporter(id, TransportState.WAITING, null, 100, 0,
                LocalDateTime.of(2026, 11, 30, 12, 0), null);
    }

    @Test
    void keepsTheOriginalObjectAndRejectsDuplicateIds() {
        Transporter original = transport("t1");
        registry.register(original);
        assertThrows(IllegalArgumentException.class, () -> registry.register(transport("t1")));
        assertSame(original, registry.get("t1"));
    }

    @Test
    void returnsSortedListWithoutExposingTheCollection() {
        Transporter b = transport("b");
        Transporter a = transport("a");
        registry.register(b);
        registry.register(a);
        List<Transporter> snapshot = registry.getAll();
        assertEquals(List.of(a, b), snapshot);
        assertThrows(UnsupportedOperationException.class, snapshot::clear);
        registry.register(transport("c"));
        assertEquals(2, snapshot.size());
        assertEquals(3, registry.getAll().size());
    }

    @Test
    void rejectsMissingTransportsAndInvalidIds() {
        assertThrows(IllegalArgumentException.class, () -> registry.get("missing"));
        assertThrows(IllegalArgumentException.class, () -> registry.get(null));
        assertThrows(IllegalArgumentException.class, () -> registry.get(" "));
        assertThrows(IllegalArgumentException.class, () -> registry.register(null));
        assertThrows(IllegalArgumentException.class, () -> registry.register(transport(null)));
        assertThrows(IllegalArgumentException.class, () -> registry.register(transport(" ")));
    }

    @Test
    void onlyOneConcurrentRegistrationCanClaimAnId() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger accepted = new AtomicInteger();
        AtomicInteger rejected = new AtomicInteger();
        Callable<Void> register = () -> {
            ready.countDown();
            assertTrue(start.await(3, TimeUnit.SECONDS));
            try {
                registry.register(transport("same-id"));
                accepted.incrementAndGet();
            } catch (IllegalArgumentException e) {
                rejected.incrementAndGet();
            }
            return null;
        };
        try {
            Future<Void> first = pool.submit(register);
            Future<Void> second = pool.submit(register);
            assertTrue(ready.await(3, TimeUnit.SECONDS));
            start.countDown();
            first.get(3, TimeUnit.SECONDS);
            second.get(3, TimeUnit.SECONDS);
            assertEquals(1, accepted.get());
            assertEquals(1, rejected.get());
            assertEquals(1, registry.getAll().size());
        } finally {
            start.countDown();
            pool.shutdownNow();
            assertTrue(pool.awaitTermination(3, TimeUnit.SECONDS));
        }
    }
}
