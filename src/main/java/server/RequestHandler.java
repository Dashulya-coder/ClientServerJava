package server;

import pipeline.CommandType;
import protocol.Message;
import warehouse.Warehouse;

import java.util.concurrent.CopyOnWriteArrayList;

// Shared command processing logic used by ProcessorWorker and TCP/UDP servers.
// Receives request (message), then executes the command on the Warehouse and
// returns a response
public class RequestHandler {

    private final Warehouse warehouse;

    public RequestHandler(Warehouse warehouse) {
        this.warehouse = warehouse;
    }

    public Message handle(Message request) {
        String result = handleCommand(request);
        return new Message(request.getCommandType(), request.getUserId(), result);
    }

    private String handleCommand(Message message) {
        // payload format "productName:amount"
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
                    warehouse.addGroup(productName, new CopyOnWriteArrayList<>());
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
}
