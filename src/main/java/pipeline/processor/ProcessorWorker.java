package pipeline.processor;

import protocol.Message;
import protocol.Package;
import server.RequestHandler;
import warehouse.Warehouse;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

public class ProcessorWorker implements Processor, Runnable {

    private final RequestHandler handler;
    private final BlockingQueue<Package> inQueue;
    private final BlockingQueue<Message> outQueue;
    private volatile boolean running = true;

    public ProcessorWorker(Warehouse warehouse,
                           BlockingQueue<Package> inQueue,
                           BlockingQueue<Message> outQueue) {
        this.handler = new RequestHandler(warehouse);
        this.inQueue = inQueue;
        this.outQueue = outQueue;
    }

    @Override
    public void process(Message message) {
        Message response = handler.handle(message);
        try {
            outQueue.put(response);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public void run() {
        while (running) {
            try {
                Package pkg = inQueue.poll(100, TimeUnit.MILLISECONDS);
                if (pkg != null) process(pkg.getMessage());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    public void stop() {
        running = false;
    }
}