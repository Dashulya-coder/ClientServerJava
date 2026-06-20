package server;

import client.StoreClientTCP;
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
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("StoreServerTCP / StoreClientTCP tests")
class StoreServerTcpTest {

    private static final byte[] KEY = {
            0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08,
            0x09, 0x0A, 0x0B, 0x0C, 0x0D, 0x0E, 0x0F, 0x10,
            0x11, 0x12, 0x13, 0x14, 0x15, 0x16, 0x17, 0x18,
            0x19, 0x1A, 0x1B, 0x1C, 0x1D, 0x1E, 0x1F, 0x20
    };

    private StoreServerTCP server;

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
    @DisplayName("single client gets a correct response")
    void singleClient_getsResponse() throws Exception {
        server = new StoreServerTCP(0, newWarehouse(), KEY);
        server.start();

        try (StoreClientTCP client = new StoreClientTCP("localhost", server.getPort(), (byte) 1, KEY)) {
            Message resp = client.send(get("Apple"));
            assertEquals("OK:1000", resp.getPayload());
        }
    }

    @Test
    @DisplayName("multiple clients update the shared warehouse concurrently")
    void multipleClients_concurrent() throws Exception {
        Warehouse warehouse = newWarehouse();
        server = new StoreServerTCP(0, warehouse, KEY);
        server.start();
        int port = server.getPort();

        int clientCount = 5;
        int perClient = 20;
        List<Thread> threads = new ArrayList<>();
        for (int c = 0; c < clientCount; c++) {
            final byte id = (byte) (c + 1);
            Thread t = new Thread(() -> {
                try (StoreClientTCP client = new StoreClientTCP("localhost", port, id, KEY)) {
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
    @DisplayName("client reconnects after the server restarts (connection loss)")
    void client_reconnects_afterServerRestart() throws Exception {
        Warehouse warehouse = newWarehouse();
        server = new StoreServerTCP(0, warehouse, KEY);
        server.start();
        int port = server.getPort();

        try (StoreClientTCP client = new StoreClientTCP(
                "localhost", port, (byte) 1, KEY, 20, 200, 1000)) {

            assertEquals("OK", client.send(add("Apple", 1)).getPayload());

            server.stop();

            AtomicReference<StoreServerTCP> restarted = new AtomicReference<>();
            Thread restartThread = new Thread(() -> {
                try {
                    Thread.sleep(600);
                    StoreServerTCP s2 = new StoreServerTCP(port, warehouse, KEY);
                    s2.start();
                    restarted.set(s2);
                } catch (Exception e) {
                    fail(e);
                }
            });
            restartThread.start();

            Message resp = client.send(add("Apple", 1));
            assertEquals("OK", resp.getPayload());

            restartThread.join();
            if (restarted.get() != null) restarted.get().stop();
        }

        assertEquals(1002, warehouse.getQuantity("Apple"));
    }

    @Test
    @DisplayName("client reports server unavailable when it never comes back")
    void client_reportsUnavailable_whenServerDown() {
        StoreClientTCP client = new StoreClientTCP(
                "localhost", 56789, (byte) 1, KEY, 3, 100, 300);
        IOException ex = assertThrows(IOException.class, () -> client.send(get("Apple")));
        assertTrue(ex.getMessage().contains("Could not connect")
                || ex.getMessage().contains("Server unavailable"));
        client.close();
    }
}
