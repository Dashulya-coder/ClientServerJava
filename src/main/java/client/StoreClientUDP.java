package client;

import protocol.CryptoService;
import protocol.Message;
import protocol.Package;
import protocol.PackageBuilder;
import protocol.PackageParser;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.util.concurrent.atomic.AtomicLong;

// UDP client with retransmission
// on timeout it resends the same packetId up to maxRetries
// packetId is used to ignore duplicate replies
public class StoreClientUDP implements AutoCloseable {

    private final InetAddress serverAddress;
    private final int serverPort;
    private final byte clientId;
    private final PackageBuilder builder;
    private final PackageParser parser;
    private final AtomicLong packetIdCounter = new AtomicLong(0);

    private final int maxRetries;
    private final int timeoutMs;
    private final DatagramSocket socket;

    //also convenience constructor
    public StoreClientUDP(String host, int port, byte clientId, byte[] key) throws IOException {
        this(host, port, clientId, key, 5, 500);
    }

    public StoreClientUDP(String host, int port, byte clientId, byte[] key,
                          int maxRetries, int timeoutMs) throws IOException {
        this.serverAddress = InetAddress.getByName(host);
        this.serverPort = port;
        this.clientId = clientId;
        CryptoService crypto = new CryptoService(key);
        this.builder = new PackageBuilder(crypto);
        this.parser = new PackageParser(crypto);
        this.maxRetries = maxRetries;
        this.timeoutMs = timeoutMs;
        this.socket = new DatagramSocket();
        this.socket.setSoTimeout(timeoutMs);
    }

    public synchronized Message send(Message request) throws IOException {
        long packetId = packetIdCounter.incrementAndGet();
        byte[] packet;
        try {
            packet = builder.build(clientId, packetId, request);
        } catch (Exception e) {
            throw new IOException("Failed to build packet", e);
        }

        DatagramPacket out = new DatagramPacket(
                packet, packet.length, serverAddress, serverPort);

        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            socket.send(out); // resend the same packet on every attempt

            Message response = awaitResponse(packetId);
            if (response != null) {
                if (attempt > 1) {
                    System.out.println("[UDP-client " + (clientId & 0xFF)
                            + "] got response for packetId " + packetId
                            + " after " + attempt + " attempts");
                }
                return response;
            }
            System.err.println("[UDP-client " + (clientId & 0xFF)
                    + "] no response for packetId " + packetId
                    + " (attempt " + attempt + "/" + maxRetries + ") — retransmitting");
        }
        throw new IOException("No response for packetId " + packetId
                + " after " + maxRetries + " attempts");
    }

    private Message awaitResponse(long expectedPacketId) throws IOException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        byte[] buffer = new byte[64 * 1024];

        while (System.currentTimeMillis() < deadline) {
            int remaining = (int) (deadline - System.currentTimeMillis());
            socket.setSoTimeout(Math.max(1, remaining));
            DatagramPacket in = new DatagramPacket(buffer, buffer.length);
            try {
                socket.receive(in);
            } catch (SocketTimeoutException e) {
                return null; // timed out, caller retransmits
            }

            byte[] raw = new byte[in.getLength()];
            System.arraycopy(in.getData(), in.getOffset(), raw, 0, in.getLength());
            try {
                Package response = parser.parse(raw);
                if (response.getPacketId() == expectedPacketId) {
                    return response.getMessage();
                }
                // ignore duplicate response
            } catch (Exception e) {
                // ignore the bad datagram and keep waiting
            }
        }
        return null;
    }

    @Override
    public void close() {
        socket.close();
    }
}
