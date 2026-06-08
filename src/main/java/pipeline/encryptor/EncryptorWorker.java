package pipeline.encryptor;

import protocol.Message;
import protocol.PackageBuilder;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicLong;

public class EncryptorWorker implements Encryptor, Runnable {

    private final PackageBuilder builder;
    private final BlockingQueue<Message> inQueue;
    private final BlockingQueue<byte[]> outQueue;
    private final AtomicLong packetIdCounter = new AtomicLong(0);
    private volatile boolean running = true;

    public EncryptorWorker(PackageBuilder builder,
                           BlockingQueue<Message> inQueue,
                           BlockingQueue<byte[]> outQueue) {
        this.builder = builder;
        this.inQueue = inQueue;
        this.outQueue = outQueue;
    }

    @Override
    public byte[] encrypt(Message message) {
        try {
            return builder.build((byte) 0, packetIdCounter.incrementAndGet(), message);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void run() {
        while (running) {
            try {
                Message message = inQueue.take();
                byte[] encrypted = encrypt(message);
                outQueue.put(encrypted);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    public void stop() {
        running = false;
    }
}