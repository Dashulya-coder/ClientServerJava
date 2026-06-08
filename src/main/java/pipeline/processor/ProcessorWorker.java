package pipeline.processor;

import pipeline.CommandType;
import protocol.Message;
import protocol.Package;
import warehouse.Warehouse;

import java.util.concurrent.BlockingQueue;

public class ProcessorWorker implements Processor, Runnable {

    private final Warehouse warehouse;
    private final BlockingQueue<Package> inQueue;
    private final BlockingQueue<Message> outQueue;
    private volatile boolean running = true;

    public ProcessorWorker(Warehouse warehouse,
                           BlockingQueue<Package> inQueue,
                           BlockingQueue<Message> outQueue) {
        this.warehouse = warehouse;
        this.inQueue = inQueue;
        this.outQueue = outQueue;
    }

    @Override
    public void process(Message message) {
        String result = handleCommand(message);
        Message response = new Message(message.getCommandType(), message.getUserId(), result);
        try {
            outQueue.put(response);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private String handleCommand(Message message) {
        // payload format: "productName:amount"
        String[] parts = message.getPayload().split(":");
        String productName = parts.length > 0 ? parts[0] : "";
        int amount = parts.length > 1 ? Integer.parseInt(parts[1]) : 0;

        CommandType command = CommandType.values()[message.getCommandType()];
        try {
            switch (command) {
                case GET_QUANTITY:
                    int qty = warehouse.getQuantity(productName);
                    return "OK:" + qty;
                case ADD_QUANTITY:
                    warehouse.addProductAmount(productName, amount);
                    return "OK";
                case REDUCE_QUANTITY:
                    warehouse.reduceProductAmount(productName, amount);
                    return "OK";
                case ADD_GROUP:
                    warehouse.addGroup(productName, new java.util.concurrent.CopyOnWriteArrayList<>());
                    return "OK";
                case ADD_PRODUCT_TO_GROUP:
                    warehouse.addProductToGroup(productName, parts.length > 1 ? parts[1] : "");
                    return "OK";
                case SET_PRICE:
                    warehouse.setPrice(productName, amount);
                    return "OK";
                default:
                    return "ERROR:unknown command";
            }
        } catch (Exception e) {
            return "ERROR:" + e.getMessage();
        }
    }

    @Override
    public void run() {
        while (running) {
            try {
                Package pkg = inQueue.take();
                process(pkg.getMessage());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    public void stop() {
        running = false;
    }
}