package server;

import db.ProductFilter;
import pipeline.CommandType;
import protocol.Message;
import service.ProductService;
import warehouse.Product;
import warehouse.Warehouse;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

// Shared command processing logic used by ProcessorWorker and TCP/UDP servers.
// CRUD commands are delegated to ProductService
public class RequestHandler {

    private final Warehouse warehouse;
    private final ProductService productService;

    public RequestHandler(Warehouse warehouse) {
        this(warehouse, null);
    }

    public RequestHandler(Warehouse warehouse, ProductService productService) {
        this.warehouse = warehouse;
        this.productService = productService;
    }

    public Message handle(Message request) {
        String result = handleCommand(request);
        return new Message(request.getCommandType(), request.getUserId(), result);
    }

    private String handleCommand(Message message) {
        CommandType command = CommandType.values()[message.getCommandType()];
        try {
            switch (command) {
                case CREATE_PRODUCT:
                case READ_PRODUCT:
                case UPDATE_PRODUCT:
                case DELETE_PRODUCT:
                case SEARCH_PRODUCTS:
                    return handleProductCommand(command, message.getPayload());
                default:
                    return handleWarehouseCommand(command, message.getPayload());
            }
        } catch (Exception e) {
            return "ERROR:" + e.getMessage();
        }
    }


    private String handleWarehouseCommand(CommandType command, String payload) {
        // payload format "productName:amount"
        String[] parts = payload.split(":");
        String productName = parts.length > 0 ? parts[0] : "";
        int amount = parts.length > 1 ? Integer.parseInt(parts[1]) : 0;

        switch (command) {
            case GET_QUANTITY:
                return "OK:" + warehouse.getQuantity(productName);
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
    }


    private String handleProductCommand(CommandType command, String payload) {
        if (productService == null) {
            return "ERROR:product service not configured";
        }
        switch (command) {
            //here are different payloads so won\t describe them all
            case CREATE_PRODUCT: {
                String[] p = payload.split(":");
                Product created = productService.create(
                        p[0], Integer.parseInt(p[1]), Integer.parseInt(p[2]),
                        p.length > 3 ? p[3] : null);
                return "OK:" + created.getId();
            }
            case READ_PRODUCT: {
                Optional<Product> found = productService.read(Integer.parseInt(payload.trim()));
                return found.map(pr -> "OK:" + format(pr)).orElse("ERROR:not found");
            }
            case UPDATE_PRODUCT: {
                String[] p = payload.split(":");
                boolean ok = productService.update(
                        Integer.parseInt(p[0]), p[1], Integer.parseInt(p[2]),
                        Integer.parseInt(p[3]), p.length > 4 ? p[4] : null);
                return ok ? "OK" : "ERROR:not found";
            }
            case DELETE_PRODUCT: {
                boolean ok = productService.delete(Integer.parseInt(payload.trim()));
                return ok ? "OK" : "ERROR:not found";
            }
            case SEARCH_PRODUCTS: {
                List<Product> found = productService.search(parseFilter(payload));
                String names = found.stream().map(Product::getName).collect(Collectors.joining(","));
                return "OK:" + names;
            }
            default:
                return "ERROR:unknown command";
        }
    }

    private String format(Product p) {
        return p.getId() + "," + p.getName() + "," + p.getQuantity()
                + "," + p.getPrice() + "," + p.getGroup();
    }

    private ProductFilter parseFilter(String payload) {
        ProductFilter.Builder b = ProductFilter.builder();
        if (payload == null || payload.isBlank()) {
            return b.build();
        }
        for (String pair : payload.split(";")) {
            String[] kv = pair.split("=", 2);
            if (kv.length != 2 || kv[1].isBlank()) {
                continue;
            }
            String key = kv[0].trim();
            String value = kv[1].trim();
            switch (key) {
                case "name": b.name(value); break;
                case "category": b.category(value); break;
                case "minQuantity": b.minQuantity(Integer.valueOf(value)); break;
                case "maxQuantity": b.maxQuantity(Integer.valueOf(value)); break;
                case "minPrice": b.minPrice(Integer.valueOf(value)); break;
                case "maxPrice": b.maxPrice(Integer.valueOf(value)); break;
                case "page": b.page(Integer.valueOf(value)); break;
                case "size": b.size(Integer.valueOf(value)); break;
                default:  break;
            }
        }
        return b.build();
    }
}
