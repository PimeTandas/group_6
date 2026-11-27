package server;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import protocol.EventMessage;
import protocol.MessageCodec;
import protocol.Response;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

@Timeout(10)
class ClientMessageWriterTest {
    private Response reply(String id) {
        return new Response(id, true, "pong", List.of());
    }

    @Test
    void drainsResponsesAndEventsInOrderBeforeClosing() throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        TestSocket socket = new TestSocket(bytes);
        try (ClientMessageWriter writer = new ClientMessageWriter(socket)) {
            var welcome = reply("welcome");
            var event = EventMessage.now("TEST_EVENT", Map.of("transport", "t1"));
            var goodbye = new Response("quit", true, "goodbye", List.of());
            writer.send(welcome);
            writer.send(event);
            writer.send(goodbye);
            writer.finish();
            assertEquals(List.of(welcome, event, goodbye), bytes.toString(StandardCharsets.UTF_8)
                    .lines().map(MessageCodec::decode).toList());
            assertTrue(socket.isClosed());
        }
    }

    @Test
    void concurrentSendersProduceCompleteMessagesExactlyOnce() throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        TestSocket socket = new TestSocket(bytes);
        ExecutorService pool = Executors.newFixedThreadPool(4);
        CountDownLatch ready = new CountDownLatch(4);
        CountDownLatch start = new CountDownLatch(1);
        try (ClientMessageWriter writer = new ClientMessageWriter(socket)) {
            List<Future<?>> senders = new ArrayList<>();
            for (int sender = 0; sender < 4; sender++) {
                int senderId = sender;
                senders.add(pool.submit(() -> {
                    ready.countDown();
                    assertTrue(start.await(2, TimeUnit.SECONDS));
                    for (int i = 0; i < 25; i++) writer.send(reply(senderId + "-" + i));
                    return null;
                }));
            }
            assertTrue(ready.await(2, TimeUnit.SECONDS));
            start.countDown();
            for (Future<?> sender : senders) sender.get(2, TimeUnit.SECONDS);
            writer.finish();
            var ids = bytes.toString(StandardCharsets.UTF_8).lines()
                    .map(line -> ((Response) MessageCodec.decode(line)).requestId()).toList();
            assertEquals(100, ids.size());
            assertEquals(100, ids.stream().distinct().count());
            for (int sender = 0; sender < 4; sender++) {
                for (int i = 0; i < 25; i++) assertTrue(ids.contains(sender + "-" + i));
            }
        } finally {
            start.countDown();
            pool.shutdownNow();
            assertTrue(pool.awaitTermination(2, TimeUnit.SECONDS));
        }
    }

    @Test
    void stalledSocketDoesNotBlockEnqueuingOrAnotherConnection() throws Exception {
        BlockingOutput stalledOutput = new BlockingOutput();
        TestSocket stalled = new TestSocket(stalledOutput);
        ByteArrayOutputStream healthyOutput = new ByteArrayOutputStream();
        TestSocket healthy = new TestSocket(healthyOutput);
        ExecutorService sender = Executors.newSingleThreadExecutor();
        try (ClientMessageWriter slowWriter = new ClientMessageWriter(stalled);
             ClientMessageWriter fastWriter = new ClientMessageWriter(healthy)) {
            slowWriter.send(reply("first"));
            assertTrue(stalledOutput.entered.await(2, TimeUnit.SECONDS));
            // the first write is still blocked when the next send and healthy connection finish
            sender.submit(() -> slowWriter.send(reply("queued"))).get(1, TimeUnit.SECONDS);
            fastWriter.send(reply("healthy"));
            fastWriter.finish();
            assertEquals("healthy", ((Response) MessageCodec.decode(healthyOutput.toString(StandardCharsets.UTF_8).trim())).requestId());
            assertFalse(stalled.isClosed());
        } finally {
            sender.shutdownNow();
            assertTrue(sender.awaitTermination(2, TimeUnit.SECONDS));
        }
    }

    @Test
    void fullQueueDisconnectsTheStalledClient() throws Exception {
        BlockingOutput output = new BlockingOutput();
        TestSocket socket = new TestSocket(output);
        ExecutorService sender = Executors.newSingleThreadExecutor();
        try (ClientMessageWriter writer = new ClientMessageWriter(socket, 2)) {
            writer.send(reply("blocked"));
            assertTrue(output.entered.await(2, TimeUnit.SECONDS));
            writer.send(reply("queued-1"));
            writer.send(reply("queued-2"));
            sender.submit(() -> writer.send(reply("overflow"))).get(1, TimeUnit.SECONDS);
            assertTrue(socket.isClosed());
            assertTrue(output.exited.await(2, TimeUnit.SECONDS));
        } finally {
            sender.shutdownNow();
            assertTrue(sender.awaitTermination(2, TimeUnit.SECONDS));
        }
    }

    @Test
    void failedWriteClosesTheSocket() throws Exception {
        TestSocket socket = new TestSocket(new OutputStream() {
            @Override
            public void write(int value) throws IOException {
                throw new IOException("connection lost");
            }
        });
        try (ClientMessageWriter writer = new ClientMessageWriter(socket)) {
            writer.send(reply("failed"));
            assertTrue(socket.closed.await(2, TimeUnit.SECONDS));
        }
    }

    private static class TestSocket extends Socket {
        private final OutputStream output;
        private final CountDownLatch closed = new CountDownLatch(1);

        TestSocket(OutputStream output) {
            this.output = output;
        }

        @Override
        public OutputStream getOutputStream() {
            return output;
        }

        @Override
        public boolean isClosed() {
            return closed.getCount() == 0;
        }

        @Override
        public void close() {
            closed.countDown();
        }
    }

    private static class BlockingOutput extends OutputStream {
        private final CountDownLatch entered = new CountDownLatch(1);
        private final CountDownLatch exited = new CountDownLatch(1);

        @Override
        public void write(int value) throws IOException {
            entered.countDown();
            try {
                new CountDownLatch(1).await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("write interrupted", e);
            } finally {
                exited.countDown();
            }
        }
    }
}
