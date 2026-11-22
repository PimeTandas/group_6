package service;

import domains.DockingBay;
import domains.Transporter;
import enums.BayStatus;
import enums.TransportState;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DepartureSchedulerTest {

    private final DockingService docking = new DockingServiceImpl();

    // makes a transport that is docked and READY
    private Transporter makeReadyTransport(String id, LocalDateTime departure, DockingBay bay) {
        Transporter transport = new Transporter(id, TransportState.WAITING, null, 100, 50, departure, null);
        docking.allocateBay(transport, bay);
        transport.setState(TransportState.SERVICING);
        transport.setState(TransportState.READY);
        return transport;
    }

    private DockingBay makeBay(String id) {
        return new DockingBay(id, BayStatus.AVAILABLE, null);
    }

    @Test
    void departsOnlyReadyTransportsThatAreDue() {
        DockingBay dueBay = makeBay("BAY-1");
        Transporter due = makeReadyTransport("T1", LocalDateTime.now().minusMinutes(1), dueBay);
        Transporter notDueYet = makeReadyTransport("T2", LocalDateTime.now().plusHours(1), makeBay("BAY-2"));

        // docked but not ready, so it must not depart even though its time has passed
        Transporter notReady = new Transporter("T3", TransportState.WAITING, null, 100, 0,
                LocalDateTime.now().minusMinutes(1), null);
        docking.allocateBay(notReady, makeBay("BAY-3"));

        List<Transporter> transports = new ArrayList<>();
        transports.add(due);
        transports.add(notDueYet);
        transports.add(notReady);

        DepartureScheduler scheduler = new DepartureScheduler(docking, transports, 1000);

        List<String> departed = new ArrayList<>();
        scheduler.setListener(t -> departed.add(t.getTransporterId()));

        scheduler.checkNow();

        assertEquals(TransportState.DEPARTED, due.getState());
        assertNull(due.getDockingBay());
        assertEquals(BayStatus.AVAILABLE, dueBay.getStatus());
        assertNull(dueBay.getOccupant());
        assertEquals(TransportState.READY, notDueYet.getState());
        assertEquals(TransportState.DOCKED, notReady.getState());
        assertEquals(1, departed.size());
        assertEquals("T1", departed.get(0));
    }

    @Test
    void backgroundThreadDepartsWithoutAnyoneCallingIt() throws Exception {
        Transporter transport = makeReadyTransport("T1", LocalDateTime.now().plusNanos(100_000_000L), makeBay("BAY-1"));
        List<Transporter> transports = new ArrayList<>();
        transports.add(transport);

        DepartureScheduler scheduler = new DepartureScheduler(docking, transports, 20);
        scheduler.start();

        // wait up to 3 seconds for the background thread to do it
        long giveUpAt = System.currentTimeMillis() + 3000;
        while (transport.getState() != TransportState.DEPARTED && System.currentTimeMillis() < giveUpAt) {
            Thread.sleep(10);
        }

        scheduler.stop();

        assertEquals(TransportState.DEPARTED, transport.getState());
    }

    @Test
    void manyChecksAtOnce_eachTransportDepartsExactlyOnce() throws Exception {
        List<Transporter> transports = new ArrayList<>();
        List<DockingBay> bays = new ArrayList<>();

        for (int i = 0; i < 50; i++) {
            DockingBay bay = makeBay("BAY-" + i);
            bays.add(bay);
            transports.add(makeReadyTransport("T" + String.format("%02d", i), LocalDateTime.now().minusSeconds(1), bay));
        }

        // lots of threads add to this list so it has to be locked when we add
        List<String> departed = new ArrayList<>();
        DepartureScheduler scheduler = new DepartureScheduler(docking, transports, 1000);
        scheduler.setListener(t -> {
            synchronized (departed) {
                departed.add(t.getTransporterId());
            }
        });

        List<Thread> threads = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            Thread thread = new Thread(() -> scheduler.checkNow());
            threads.add(thread);
            thread.start();
        }
        for (Thread thread : threads) {
            thread.join();
        }

        // 50 transports and 8 threads all checking, so exactly 50 departures with no repeats
        assertEquals(50, departed.size());
        assertEquals(50, new HashSet<>(departed).size());

        for (Transporter transport : transports) {
            assertEquals(TransportState.DEPARTED, transport.getState());
            assertNull(transport.getDockingBay());
        }
        for (DockingBay bay : bays) {
            assertEquals(BayStatus.AVAILABLE, bay.getStatus());
            assertNull(bay.getOccupant());
        }
    }

    @Test
    void departureRacingWithCargo_cargoNeverChangesAfterDeparture() throws Exception {
        CargoService cargo = new CargoServiceImpl();

        for (int round = 0; round < 100; round++) {
            Transporter transport = makeReadyTransport("T1", LocalDateTime.now().minusSeconds(1), makeBay("BAY-1"));
            List<Transporter> transports = new ArrayList<>();
            transports.add(transport);
            DepartureScheduler scheduler = new DepartureScheduler(docking, transports, 1000);

            boolean[] loaded = new boolean[1];

            Thread departThread = new Thread(() -> scheduler.checkNow());

            Thread loadThread = new Thread(() -> {
                try {
                    cargo.loadCargo(transport, 10);
                    loaded[0] = true;
                } catch (IllegalStateException departedFirst) {
                    // transport had already left so loading was refused
                }
            });

            departThread.start();
            loadThread.start();
            departThread.join();
            loadThread.join();

            assertEquals(TransportState.DEPARTED, transport.getState());

            // started with 50, either the load went through before departure (60) or it was refused (50)
            if (loaded[0]) {
                assertEquals(60, transport.getCurrentQuantity());
            } else {
                assertEquals(50, transport.getCurrentQuantity());
            }
        }
    }

    @Test
    void failingListenerDoesNotStopOtherDepartures() {
        Transporter a = makeReadyTransport("A", LocalDateTime.now().minusSeconds(1), makeBay("BAY-A"));
        Transporter b = makeReadyTransport("B", LocalDateTime.now().minusSeconds(1), makeBay("BAY-B"));
        List<Transporter> transports = new ArrayList<>();
        transports.add(a);
        transports.add(b);

        DepartureScheduler scheduler = new DepartureScheduler(docking, transports, 1000);
        scheduler.setListener(t -> {
            if (t == a) {
                throw new RuntimeException("listener broke");
            }
        });

        scheduler.checkNow();

        assertEquals(TransportState.DEPARTED, a.getState());
        assertEquals(TransportState.DEPARTED, b.getState());
    }
}
