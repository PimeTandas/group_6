package server;

import protocol.Message;
import protocol.MessageCodec;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

final class ClientMessageWriter implements AutoCloseable {
    private final Socket socket;
    private final PrintWriter output;
    private final BlockingQueue<String> pending;
    private final Object queueLock = new Object();
    private final Thread worker;
    private volatile boolean accepting = true;

    ClientMessageWriter(Socket socket) throws IOException {
        this(socket, 256);
    }

    ClientMessageWriter(Socket socket, int capacity) throws IOException {
        this.socket = socket;
        pending = new ArrayBlockingQueue<>(capacity);
        output = new PrintWriter(socket.getOutputStream(), true, StandardCharsets.UTF_8);
        worker = new Thread(this::writeMessages, "client-writer-" + socket.getPort());
        worker.setDaemon(true);
        worker.start();
    }

    void send(Message message) {
        String line = MessageCodec.encode(message);
        synchronized (queueLock) {
            if (!accepting || socket.isClosed()) {
                return;
            }
            if (pending.offer(line)) {
                return;
            }
            accepting = false;
        }
        // disconnect a stalled terminal instead of blocking other clients or dropping events
        close();
    }

    private void writeMessages() {
        try {
            while (accepting || !pending.isEmpty()) {
                String line = pending.poll(100, TimeUnit.MILLISECONDS);
                if (line != null) {
                    output.println(line);
                    if (output.checkError()) {
                        break;
                    }
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            close();
        }
    }

    void finish() {
        synchronized (queueLock) {
            accepting = false;
        }
        try {
            // allow queued replies, including goodbye, to reach the terminal before closing
            worker.join(2000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            close();
        }
    }

    @Override
    public void close() {
        synchronized (queueLock) {
            accepting = false;
        }
        try {
            socket.close();
        } catch (IOException e) {
            System.err.println("could not close client socket: " + e.getMessage());
        } finally {
            worker.interrupt();
        }
    }
}
