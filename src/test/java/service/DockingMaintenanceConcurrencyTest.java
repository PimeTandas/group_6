package service;

import domains.DockingBay;
import domains.MaintenanceTeam;
import domains.Transporter;
import enums.BayStatus;
import enums.MaintenanceStatus;
import enums.TransportState;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DockingMaintenanceConcurrencyTest {

    private final DockingService docking = new DockingServiceImpl();
    private final MaintenanceService maintenance = new MaintenanceServiceImpl();

    private Transporter makeTransport(String id, TransportState state) {
        return new Transporter(id, state, null, 100, 0, LocalDateTime.now().plusDays(1), null);
    }

    private DockingBay makeBay(String id) {
        return new DockingBay(id, BayStatus.AVAILABLE, null);
    }

    // One thread that tries one operation and remembers what happened.
    private static class Attempt extends Thread {
        private final Runnable task;
        boolean worked = false;
        Throwable unexpectedProblem = null;

        Attempt(Runnable task) {
            this.task = task;
        }

        @Override
        public void run() {
            try {
                task.run();
                worked = true;
            } catch (IllegalStateException | IllegalArgumentException refused) {
                // the operation was refused, that is allowed
            } catch (Throwable problem) {
                unexpectedProblem = problem;
            }
        }
    }

    // Starts one thread per task so they all run at the same time and waits for them.
    // Returns how many of the operations worked. A thread that never finishes
    // means a deadlock, so that fails the test.
    private int runAtSameTime(List<Runnable> tasks) throws InterruptedException {

        List<Attempt> attempts = new ArrayList<>();
        for (Runnable task : tasks) {
            attempts.add(new Attempt(task));
        }

        for (Attempt attempt : attempts) {
            attempt.start();
        }

        int worked = 0;
        for (Attempt attempt : attempts) {
            attempt.join(10000);
            assertFalse(attempt.isAlive(), "a thread is stuck, probably a deadlock");
            assertNull(attempt.unexpectedProblem, "unexpected error in a thread: " + attempt.unexpectedProblem);
            if (attempt.worked) {
                worked++;
            }
        }
        return worked;
    }

    // The tests below repeat 200 times because a race only goes wrong now and then,
    // so one run passing doesn't prove much.

    @Test
    void manyTransportsFightForOneBay_onlyOneWins() throws Exception {
        for (int round = 0; round < 200; round++) {
            DockingBay bay = makeBay("BAY-01");
            List<Transporter> transports = new ArrayList<>();
            List<Runnable> tasks = new ArrayList<>();

            for (int i = 0; i < 20; i++) {
                Transporter transport = makeTransport("T" + i, TransportState.WAITING);
                transports.add(transport);
                tasks.add(() -> docking.allocateBay(transport, bay));
            }

            assertEquals(1, runAtSameTime(tasks));
            assertEquals(BayStatus.OCCUPIED, bay.getStatus());

            int docked = 0;
            for (Transporter transport : transports) {
                if (transport.getState() == TransportState.DOCKED) {
                    docked++;
                }
            }
            assertEquals(1, docked);
            assertSame(bay, bay.getOccupant().getDockingBay());
        }
    }

    @Test
    void oneTransportGrabsManyBays_onlyOneBayTaken() throws Exception {
        for (int round = 0; round < 200; round++) {
            Transporter transport = makeTransport("T1", TransportState.WAITING);
            List<DockingBay> bays = new ArrayList<>();
            List<Runnable> tasks = new ArrayList<>();

            for (int i = 0; i < 10; i++) {
                DockingBay bay = makeBay("BAY-" + i);
                bays.add(bay);
                tasks.add(() -> docking.allocateBay(transport, bay));
            }

            assertEquals(1, runAtSameTime(tasks));

            int occupied = 0;
            for (DockingBay bay : bays) {
                if (bay.getStatus() == BayStatus.OCCUPIED) {
                    occupied++;
                }
            }
            assertEquals(1, occupied);
        }
    }

    @Test
    void closeVersusAllocate_neverClosedAndOccupied() throws Exception {
        for (int round = 0; round < 200; round++) {
            DockingBay bay = makeBay("BAY-01");
            Transporter transport = makeTransport("T1", TransportState.WAITING);

            List<Runnable> tasks = new ArrayList<>();
            tasks.add(() -> docking.allocateBay(transport, bay));
            tasks.add(() -> docking.closeBay(bay));
            runAtSameTime(tasks);

            if (bay.getStatus() == BayStatus.CLOSED) {
                // close won, so the transport must not have been docked
                assertNull(bay.getOccupant());
                assertNull(transport.getDockingBay());
                assertEquals(TransportState.WAITING, transport.getState());
            } else {
                // allocate won, both sides must agree
                assertEquals(BayStatus.OCCUPIED, bay.getStatus());
                assertSame(transport, bay.getOccupant());
                assertSame(bay, transport.getDockingBay());
            }
        }
    }

    @Test
    void oppositeExchangesAtOnce_noDeadlockAndStayConsistent() throws Exception {
        for (int round = 0; round < 50; round++) {
            Transporter a = makeTransport("A", TransportState.WAITING);
            Transporter b = makeTransport("B", TransportState.WAITING);
            DockingBay bayA = makeBay("BAY-A");
            DockingBay bayB = makeBay("BAY-B");
            docking.allocateBay(a, bayA);
            docking.allocateBay(b, bayB);

            List<Runnable> tasks = new ArrayList<>();
            for (int i = 0; i < 20; i++) {
                tasks.add(() -> docking.exchangeBays(a, b));
                tasks.add(() -> docking.exchangeBays(b, a));
            }
            runAtSameTime(tasks);

            assertNotSame(a.getDockingBay(), b.getDockingBay());
            assertSame(a, a.getDockingBay().getOccupant());
            assertSame(b, b.getDockingBay().getOccupant());
        }
    }

    @Test
    void failedExchangeChangesNothing() {
        Transporter docked = makeTransport("A", TransportState.WAITING);
        Transporter waiting = makeTransport("B", TransportState.WAITING);
        DockingBay bay = makeBay("BAY-A");
        docking.allocateBay(docked, bay);

        assertThrows(IllegalStateException.class, () -> docking.exchangeBays(docked, waiting));

        assertSame(bay, docked.getDockingBay());
        assertSame(docked, bay.getOccupant());
        assertNull(waiting.getDockingBay());
    }

    @Test
    void manyTeamsToOneTransport_onlyOneAssigned() throws Exception {
        for (int round = 0; round < 200; round++) {
            Transporter transport = makeTransport("T1", TransportState.DOCKED);
            List<MaintenanceTeam> teams = new ArrayList<>();
            List<Runnable> tasks = new ArrayList<>();

            for (int i = 0; i < 10; i++) {
                MaintenanceTeam team = new MaintenanceTeam("TEAM-" + i, MaintenanceStatus.AVAILABLE, null);
                teams.add(team);
                tasks.add(() -> maintenance.assignTeam(transport, team));
            }

            assertEquals(1, runAtSameTime(tasks));
            assertEquals(TransportState.SERVICING, transport.getState());

            int busy = 0;
            for (MaintenanceTeam team : teams) {
                if (team.getMaintenanceStatus() == MaintenanceStatus.BUSY) {
                    busy++;
                }
            }
            assertEquals(1, busy);
            assertSame(transport, transport.getMaintenanceTeam().getTransporter());
        }
    }

    @Test
    void oneTeamToManyTransports_onlyOneAssigned() throws Exception {
        for (int round = 0; round < 200; round++) {
            MaintenanceTeam team = new MaintenanceTeam("TEAM-1", MaintenanceStatus.AVAILABLE, null);
            List<Transporter> transports = new ArrayList<>();
            List<Runnable> tasks = new ArrayList<>();

            for (int i = 0; i < 10; i++) {
                Transporter transport = makeTransport("T" + i, TransportState.DOCKED);
                transports.add(transport);
                tasks.add(() -> maintenance.assignTeam(transport, team));
            }

            assertEquals(1, runAtSameTime(tasks));

            int servicing = 0;
            for (Transporter transport : transports) {
                if (transport.getState() == TransportState.SERVICING) {
                    servicing++;
                }
            }
            assertEquals(1, servicing);
            assertSame(team, team.getTransporter().getMaintenanceTeam());
        }
    }

    @Test
    void departureRacingWithExchange_staysConsistent() throws Exception {
        for (int round = 0; round < 200; round++) {
            Transporter a = new Transporter("A", TransportState.WAITING, null, 100, 0,
                    LocalDateTime.now().minusMinutes(1), null);
            Transporter b = makeTransport("B", TransportState.WAITING);
            DockingBay bayA = makeBay("BAY-A");
            DockingBay bayB = makeBay("BAY-B");
            docking.allocateBay(a, bayA);
            docking.allocateBay(b, bayB);

            a.setState(TransportState.SERVICING);
            a.setState(TransportState.READY);
            b.setState(TransportState.SERVICING);
            b.setState(TransportState.READY);

            List<Runnable> tasks = new ArrayList<>();
            tasks.add(() -> docking.departTransport(a, LocalDateTime.now()));
            tasks.add(() -> docking.exchangeBays(a, b));
            runAtSameTime(tasks);

            // a always departs, whether the exchange happened before or not
            assertEquals(TransportState.DEPARTED, a.getState());
            assertNull(a.getDockingBay());

            // each bay is either empty, or its occupant points back at it
            if (bayA.getStatus() == BayStatus.AVAILABLE) {
                assertNull(bayA.getOccupant());
            } else {
                assertSame(bayA, bayA.getOccupant().getDockingBay());
            }
            if (bayB.getStatus() == BayStatus.AVAILABLE) {
                assertNull(bayB.getOccupant());
            } else {
                assertSame(bayB, bayB.getOccupant().getDockingBay());
            }
            assertSame(b, b.getDockingBay().getOccupant());
        }
    }
}
