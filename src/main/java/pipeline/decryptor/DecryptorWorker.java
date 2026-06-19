package pipeline.decryptor;

import protocol.Package;
import protocol.PackageParser;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

public class DecryptorWorker implements Decryptor, Runnable {

    private final PackageParser parser;
    private final BlockingQueue<byte[]> inQueue;
    private final BlockingQueue<Package> outQueue;
    private volatile boolean running = true;

    public DecryptorWorker(PackageParser parser,
                           BlockingQueue<byte[]> inQueue,
                           BlockingQueue<Package> outQueue) {
        this.parser = parser;
        this.inQueue = inQueue;
        this.outQueue = outQueue;
    }

    @Override
    public void decrypt(byte[] message) {
        try {
            Package pkg = parser.parse(message);
            outQueue.put(pkg);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            System.err.println("Failed to decrypt: " + e.getMessage());
        }
    }

    @Override
    public void run() {
        while (running) {
            try {
                byte[] raw = inQueue.poll(100, TimeUnit.MILLISECONDS);
                if (raw != null) decrypt(raw);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    public void stop() {
        running = false;
    }
}