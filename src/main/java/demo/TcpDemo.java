package demo;

import client.StoreClientTCP;
import protocol.Message;
import server.StoreServerTCP;
import warehouse.Warehouse;

import java.util.ArrayList;
import java.util.List;

// Runs one TCP server and several concurrent clients.
// Run: mvn compile exec:java -Dexec.mainClass=demo.TcpDemo
public class TcpDemo {

    private static final int CLIENT_COUNT = 3;
    private static final int REQUESTS_PER_CLIENT = 5;

    public static void main(String[] args) throws Exception {
        Warehouse warehouse = DemoSupport.sampleWarehouse();
        StoreServerTCP server = new StoreServerTCP(DemoSupport.TCP_PORT, warehouse, DemoSupport.KEY);
        server.start();

        List<Thread> clients = new ArrayList<>();
        for (int c = 0; c < CLIENT_COUNT; c++) {
            final byte clientId = (byte) (c + 1);
            Thread t = new Thread(() -> runClient(clientId), "client-" + clientId);
            clients.add(t);
            t.start();
        }
        for (Thread t : clients) {
            t.join();
        }

        // Final state via a fresh client.
        try (StoreClientTCP probe =
                     new StoreClientTCP("localhost", DemoSupport.TCP_PORT, (byte) 99, DemoSupport.KEY)) {
            System.out.println("[TCP] final Apple = "
                    + probe.send(DemoSupport.get("Apple")).getPayload());
        }

        server.stop();
    }

    private static void runClient(byte clientId) {
        try (StoreClientTCP client =
                     new StoreClientTCP("localhost", DemoSupport.TCP_PORT, clientId, DemoSupport.KEY)) {
            for (int i = 0; i < REQUESTS_PER_CLIENT; i++) {
                Message resp = client.send(DemoSupport.add("Apple", 1, clientId & 0xFF));
                System.out.println("[client " + (clientId & 0xFF) + "] add Apple -> "
                        + resp.getPayload());
                Thread.sleep(50);
            }
        } catch (Exception e) {
            System.err.println("[client " + (clientId & 0xFF) + "] error: " + e.getMessage());
        }
    }
}
