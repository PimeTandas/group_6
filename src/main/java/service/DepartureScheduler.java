package service;

import domains.Transporter;

import java.time.LocalDateTime;
import java.util.function.Supplier;
import java.util.ArrayList;
import java.util.List;

// Background thread that keeps checking if any transport is ready to depart.
public class DepartureScheduler implements Runnable {

    // something that wants to know when a transport leaves (event log, notifications later)
    public interface DepartureListener {
        void transportDeparted(Transporter transport);
    }

    private final DockingService dockingService;
    private final Supplier<List<Transporter>> transports;
    private final long checkEveryMillis;

    private DepartureListener listener;
    private Thread thread;
    private boolean running = false;

    public DepartureScheduler(DockingService dockingService,
                              Supplier<List<Transporter>> transports,
                              long checkEveryMillis) {
        this.dockingService = dockingService;
        this.transports = transports;
        this.checkEveryMillis = checkEveryMillis;
    }

    public void setListener(DepartureListener listener) {
        this.listener = listener;
    }

    public synchronized void start() {
        if (running) {
            throw new IllegalStateException("Departure scheduler is already running.");
        }
        running = true;
        thread = new Thread(this, "departure-scheduler");
        thread.start();

    }

    // tells the thread to stop and waits for it, so a departure that is
    // half way through finishes before the server shuts down
    public void stop() {
        Thread threadToWaitFor;

        synchronized (this) {
            running = false;
            threadToWaitFor = thread;
            thread = null;
        }

        if (threadToWaitFor != null) {
            try {
                threadToWaitFor.interrupt();
                threadToWaitFor.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    private synchronized boolean isRunning() {
        return running;
    }

    @Override
    public void run() {
        while (isRunning()) {
            checkNow();
            try {
                Thread.sleep(checkEveryMillis);
            } catch (InterruptedException e) {
                return;
            }
        }
    }

    // one check of every transport, public so the tests can call it directly
    public void checkNow() {

        List<Transporter> copy = transports.get();

        LocalDateTime now = LocalDateTime.now();

        for (Transporter transport : copy) {
            try {
                boolean departed = dockingService.departTransport(transport, now);

                if (departed && listener != null) {
                    listener.transportDeparted(transport);
                }
            } catch (RuntimeException e) {
                // if this threw out of run() the thread would die and nothing would ever depart again
                System.err.println("Departure check failed for "
                        + transport.getTransporterId() + ": " + e.getMessage());
            }
        }
    }
}
