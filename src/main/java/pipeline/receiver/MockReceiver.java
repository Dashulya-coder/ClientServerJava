package pipeline.receiver;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

public class MockReceiver implements Receiver, Runnable {

    private final MessageSource messageSource;
    private final BlockingQueue<byte[]> queue;
    private volatile boolean running = true;

    public MockReceiver(MessageSource messageSource, BlockingQueue<byte[]> queue) {
        this.messageSource = messageSource;
        this.queue = queue;
    }

    @Override
    public void receiveMessage() {
        byte[] message = messageSource.generate();
        try {
            while (running && !queue.offer(message, 100, TimeUnit.MILLISECONDS)) {

            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public void run() {
        while (running) {
            receiveMessage();
        }
    }

    public void stop() {
        running = false;
    }
}