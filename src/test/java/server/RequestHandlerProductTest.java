package server;

import db.SqliteProductRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pipeline.CommandType;
import protocol.Message;
import service.ProductService;
import warehouse.Warehouse;

import static org.junit.jupiter.api.Assertions.*;


@DisplayName("RequestHandler product commands (integration)")
class RequestHandlerProductTest {

    private SqliteProductRepository repository;
    private RequestHandler handler;

    @BeforeEach
    void setUp() {
        repository = new SqliteProductRepository("jdbc:sqlite::memory:");
        ProductService service = new ProductService(repository);
        handler = new RequestHandler(new Warehouse(), service);
    }

    @AfterEach
    void tearDown() {
        repository.close();
    }

    private String send(CommandType type, String payload) {
        Message resp = handler.handle(new Message(type.ordinal(), 1, payload));
        return resp.getPayload();
    }

    @Test
    @DisplayName("create then read a product via commands")
    void createThenRead() {
        String created = send(CommandType.CREATE_PRODUCT, "Apple:100:10:Fruits");
        assertTrue(created.startsWith("OK:"));
        String id = created.substring(3);

        String read = send(CommandType.READ_PRODUCT, id);
        assertTrue(read.contains("Apple"));
        assertTrue(read.contains("Fruits"));
    }

    @Test
    @DisplayName("update and delete via commands")
    void updateThenDelete() {
        String id = send(CommandType.CREATE_PRODUCT, "Banana:50:5:Fruits").substring(3);

        assertEquals("OK", send(CommandType.UPDATE_PRODUCT, id + ":Banana:77:9:Fruits"));
        assertTrue(send(CommandType.READ_PRODUCT, id).contains("77"));

        assertEquals("OK", send(CommandType.DELETE_PRODUCT, id));
        assertEquals("ERROR:not found", send(CommandType.READ_PRODUCT, id));
    }

    @Test
    @DisplayName("search with dynamic filter via command")
    void searchWithFilter() {
        send(CommandType.CREATE_PRODUCT, "Apple:100:10:Fruits");
        send(CommandType.CREATE_PRODUCT, "Carrot:200:3:Vegetables");
        send(CommandType.CREATE_PRODUCT, "Pineapple:10:25:Fruits");

        String result = send(CommandType.SEARCH_PRODUCTS, "category=Fruits;minPrice=20");
        assertEquals("OK:Pineapple", result);
    }

    @Test
    @DisplayName("new commands report error when service not configured")
    void noService_returnsError() {
        RequestHandler noDb = new RequestHandler(new Warehouse()); // no ProductService
        Message resp = noDb.handle(new Message(CommandType.CREATE_PRODUCT.ordinal(), 1, "X:1:1:Y"));
        assertTrue(resp.getPayload().contains("not configured"));
    }
}
