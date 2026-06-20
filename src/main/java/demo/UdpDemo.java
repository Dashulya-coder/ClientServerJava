package demo;

import client.StoreClientUDP;
import protocol.Message;
import server.StoreServerUDP;
import warehouse.Warehouse;

import java.util.ArrayList;
import java.util.List;

// Runs one UDP server (with simulated packet loss) and several concurrent clients;
// clients retransmit lost packets so requests still succeed.
// Run: mvn compile exec:java -Dexec.mainClass=demo.UdpDemo
public class UdpDemo {

    private static final int CLIENT_COUNT = 3;
    private static final int REQUESTS_PER_CLIENT = 5;
    private static final double LOSS_PROBABILITY = 0.3; // drop 30% of datagrams

    public static void main(String[] args) throws Exception {
        Warehouse warehouse = DemoSupport.sampleWarehouse();
        StoreServerUDP server = new StoreServerUDP(
                DemoSupport.UDP_PORT, warehouse, DemoSupport.KEY, LOSS_PROBABILITY);
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

        try (StoreClientUDP probe =
                     new StoreClientUDP("localhost", DemoSupport.UDP_PORT, (byte) 99, DemoSupport.KEY)) {
            System.out.println("[UDP] final Apple = "
                    + probe.send(DemoSupport.get("Apple")).getPayload());
        }

        server.stop();
    }

    private static void runClient(byte clientId) {
        try (StoreClientUDP client =
                     new StoreClientUDP("localhost", DemoSupport.UDP_PORT, clientId, DemoSupport.KEY)) {
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
