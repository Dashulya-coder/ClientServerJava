package pipeline.sender;

import java.net.InetAddress;
import java.util.concurrent.BlockingQueue;

public class MockSender implements Sender, Runnable {

    private final BlockingQueue<byte[]> inQueue;
    private volatile boolean running = true;

    public MockSender(BlockingQueue<byte[]> inQueue) {
        this.inQueue = inQueue;
    }

    @Override
    public void send(byte[] message, InetAddress target) {
        System.out.println("Sending " + message.length + " bytes to " + target);
    }

    @Override
    public void run() {
        while (running) {
            try {
                byte[] message = inQueue.take();
                send(message, null);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    public void stop() {
        running = false;
    }
}