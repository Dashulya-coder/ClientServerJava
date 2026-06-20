package server;

import client.StoreClientUDP;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pipeline.CommandType;
import protocol.Message;
import warehouse.Product;
import warehouse.Warehouse;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("StoreServerUDP / StoreClientUDP tests")
class StoreServerUdpTest {

    private static final byte[] KEY = {
            0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08,
            0x09, 0x0A, 0x0B, 0x0C, 0x0D, 0x0E, 0x0F, 0x10,
            0x11, 0x12, 0x13, 0x14, 0x15, 0x16, 0x17, 0x18,
            0x19, 0x1A, 0x1B, 0x1C, 0x1D, 0x1E, 0x1F, 0x20
    };

    private StoreServerUDP server;

    private Warehouse newWarehouse() {
        Warehouse w = new Warehouse();
        w.addProduct(new Product("Apple", new AtomicInteger(1000), 10, "Fruits"));
        return w;
    }

    private Message get(String product) {
        return new Message(CommandType.GET_QUANTITY.ordinal(), 0, product + ":0");
    }

    private Message add(String product, int amount) {
        return new Message(CommandType.ADD_QUANTITY.ordinal(), 1, product + ":" + amount);
    }

    @AfterEach
    void tearDown() {
        if (server != null) server.stop();
    }

    @Test
    @DisplayName("single client gets a correct response over UDP")
    void singleClient_getsResponse() throws Exception {
        server = new StoreServerUDP(0, newWarehouse(), KEY);
        server.start();

        try (StoreClientUDP client = new StoreClientUDP("localhost", server.getPort(), (byte) 1, KEY)) {
            assertEquals("OK:1000", client.send(get("Apple")).getPayload());
        }
    }

    @Test
    @DisplayName("client retransmits and still succeeds despite 40% packet loss")
    void client_retransmits_underPacketLoss() throws Exception {
        Warehouse warehouse = newWarehouse();
        server = new StoreServerUDP(0, warehouse, KEY, 0.4); // drop 40% of datagrams
        server.start();

        // Generous retries/timeout so retransmission can overcome the loss.
        try (StoreClientUDP client = new StoreClientUDP(
                "localhost", server.getPort(), (byte) 1, KEY, 30, 200)) {
            for (int i = 0; i < 20; i++) {
                assertEquals("OK", client.send(add("Apple", 1)).getPayload());
            }
        }
        // idempotency cache guarantees no command is double-applied
        assertEquals(1020, warehouse.getQuantity("Apple"));
    }

    @Test
    @DisplayName("multiple UDP clients update the shared warehouse")
    void multipleClients_concurrent() throws Exception {
        Warehouse warehouse = newWarehouse();
        server = new StoreServerUDP(0, warehouse, KEY);
        server.start();
        int port = server.getPort();

        int clientCount = 4;
        int perClient = 15;
        List<Thread> threads = new ArrayList<>();
        for (int c = 0; c < clientCount; c++) {
            final byte id = (byte) (c + 1);
            Thread t = new Thread(() -> {
                try (StoreClientUDP client = new StoreClientUDP("localhost", port, id, KEY, 30, 200)) {
                    for (int i = 0; i < perClient; i++) {
                        assertEquals("OK", client.send(add("Apple", 1)).getPayload());
                    }
                } catch (IOException e) {
                    fail(e);
                }
            });
            threads.add(t);
            t.start();
        }
        for (Thread t : threads) t.join();

        assertEquals(1000 + clientCount * perClient, warehouse.getQuantity("Apple"));
    }

    @Test
    @DisplayName("client throws after exhausting retries when server is down")
    void client_throws_whenServerDown() throws Exception {
        try (StoreClientUDP client = new StoreClientUDP(
                "localhost", 59998, (byte) 1, KEY, 3, 100)) {
            assertThrows(IOException.class, () -> client.send(get("Apple")));
        }
    }
}
