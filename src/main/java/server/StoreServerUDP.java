package server;

import protocol.CryptoService;
import protocol.Message;
import protocol.Package;
import protocol.PackageBuilder;
import protocol.PackageParser;
import warehouse.Warehouse;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

// UDP server is idempotent under retransmission - caches the reply per
// (clientId, packetId), so resending the same request never runs the command twice

public class StoreServerUDP {

    private static final int BUFFER_SIZE = 64 * 1024;

    private final int requestedPort;
    private final RequestHandler handler;
    private final PackageBuilder builder;
    private final PackageParser parser;
    private final double lossProbability; // used to let us drop some datagrams on purpose for testing
    private final Random random = new Random();

    // idempotency cache: (clientId, packetId) -> reply bytes
    private final Map<Long, byte[]> responseCache = new ConcurrentHashMap<>();

    private volatile boolean running = false;
    private DatagramSocket socket;
    private Thread receiveThread;

    public StoreServerUDP(int port, Warehouse warehouse, byte[] key) {
        this(port, warehouse, key, 0.0, null);
    }

    public StoreServerUDP(int port, Warehouse warehouse, byte[] key, double lossProbability) {
        this(port, warehouse, key, lossProbability, null);
    }

    public StoreServerUDP(int port, Warehouse warehouse, byte[] key,
                          double lossProbability, service.ProductService productService) {
        this.requestedPort = port;
        this.handler = new RequestHandler(warehouse, productService);
        CryptoService crypto = new CryptoService(key);
        this.builder = new PackageBuilder(crypto);
        this.parser = new PackageParser(crypto);
        this.lossProbability = lossProbability;
    }

    public void start() throws IOException {
        socket = new DatagramSocket(requestedPort);
        running = true;
        receiveThread = new Thread(this::receiveLoop, "udp-receive");
        receiveThread.start();
        System.out.println("[UDP] server listening on port " + getPort()
                + (lossProbability > 0 ? " (lossProbability=" + lossProbability + ")" : ""));
    }

    public int getPort() {
        return socket != null ? socket.getLocalPort() : requestedPort;
    }

    private void receiveLoop() {
        byte[] buffer = new byte[BUFFER_SIZE];
        while (running) {
            DatagramPacket datagram = new DatagramPacket(buffer, buffer.length);
            try {
                socket.receive(datagram);
            } catch (IOException e) {
                if (running) System.err.println("[UDP] receive failed: " + e.getMessage());
                continue;
            }

            if (shouldDrop()) {
                System.out.println("[UDP] (simulated loss) dropped inbound datagram");
                continue;
            }

            byte[] raw = new byte[datagram.getLength()];
            System.arraycopy(datagram.getData(), datagram.getOffset(), raw, 0, datagram.getLength());
            handleDatagram(raw, datagram);
        }
    }

    private void handleDatagram(byte[] raw, DatagramPacket from) {
        try {
            Package request = parser.parse(raw);
            long key = cacheKey(request.getClientId(), request.getPacketId());

            // run the command only the first time; if we've seen this request
            // before, just send back the saved reply
            byte[] reply = responseCache.get(key);
            if (reply == null) {
                Message response = handler.handle(request.getMessage());
                reply = builder.build(request.getClientId(), request.getPacketId(), response);
                responseCache.put(key, reply);
            } else {
                System.out.println("[UDP] duplicate packetId " + request.getPacketId()
                        + " from client " + (request.getClientId() & 0xFF)
                        + " — resending cached response");
            }

            if (shouldDrop()) { // pretend this reply got lost
                System.out.println("[UDP] (simulated loss) dropped outbound reply for packetId "
                        + request.getPacketId());
                return;
            }

            DatagramPacket replyPacket = new DatagramPacket(
                    reply, reply.length, from.getAddress(), from.getPort());
            socket.send(replyPacket);
        } catch (Exception e) {
            System.err.println("[UDP] failed to process datagram: " + e.getMessage());
        }
    }

    private boolean shouldDrop() {
        return lossProbability > 0 && random.nextDouble() < lossProbability;
    }

    private static long cacheKey(byte clientId, long packetId) {
        return ((long) (clientId & 0xFF) << 56) ^ packetId;
    }

    public void stop() {
        running = false;
        if (socket != null) socket.close();
        System.out.println("[UDP] server stopped");
    }
}
