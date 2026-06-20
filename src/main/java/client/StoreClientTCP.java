package client;

import protocol.CryptoService;
import protocol.Message;
import protocol.Package;
import protocol.PackageBuilder;
import protocol.PackageParser;
import server.PacketReader;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.concurrent.atomic.AtomicLong;

public class StoreClientTCP implements AutoCloseable {

    private final String host;
    private final int port;
    private final byte clientId;
    private final PackageBuilder builder;
    private final PackageParser parser;
    private final AtomicLong packetIdCounter = new AtomicLong(0);

    private final int maxReconnectAttempts;
    private final long reconnectDelayMs;
    private final int readTimeoutMs;

    private Socket socket;
    private DataInputStream in;
    private DataOutputStream out;

    // convenience constructor with default reconnect settings
    public StoreClientTCP(String host, int port, byte clientId, byte[] key) {
        this(host, port, clientId, key, 10, 500, 3000);
    }

    public StoreClientTCP(String host, int port, byte clientId, byte[] key,
                          int maxReconnectAttempts, long reconnectDelayMs, int readTimeoutMs) {
        this.host = host;
        this.port = port;
        this.clientId = clientId;
        CryptoService crypto = new CryptoService(key);
        this.builder = new PackageBuilder(crypto);
        this.parser = new PackageParser(crypto);
        this.maxReconnectAttempts = maxReconnectAttempts;
        this.reconnectDelayMs = reconnectDelayMs;
        this.readTimeoutMs = readTimeoutMs;
    }

    public synchronized Message send(Message request) throws IOException {
        long packetId = packetIdCounter.incrementAndGet();
        byte[] packet;
        try {
            packet = builder.build(clientId, packetId, request);
        } catch (Exception e) {
            throw new IOException("Failed to build packet", e);
        }

        // first failure triggers a reconnect, then we retry on the fresh connection
        IOException last = null;
        for (int attempt = 0; attempt <= 1; attempt++) {
            ensureConnected(); // blocks until connected

            try {
                out.write(packet);
                out.flush();
                byte[] raw = PacketReader.readPacket(in);
                Package response = parser.parse(raw);
                return response.getMessage();
            } catch (IOException e) {
                last = e;
                System.err.println("[TCP-client " + (clientId & 0xFF)
                        + "] connection lost: " + e.getMessage() + " — will reconnect");
                closeQuietly();
            } catch (Exception e) {
                throw new IOException("Failed to parse response", e);
            }
        }
        throw new IOException("Server unavailable", last);
    }

    public synchronized boolean isConnected() {
        return socket != null && socket.isConnected() && !socket.isClosed();
    }

    // reconnects until connected or attempts run out
    private void ensureConnected() throws IOException {
        if (isConnected()) {
            return;
        }
        IOException last = null;
        for (int attempt = 1; attempt <= maxReconnectAttempts; attempt++) {
            try {
                Socket s = new Socket();
                s.connect(new InetSocketAddress(host, port), readTimeoutMs);
                s.setSoTimeout(readTimeoutMs);
                this.socket = s;
                this.in = new DataInputStream(s.getInputStream());
                this.out = new DataOutputStream(s.getOutputStream());
                if (attempt > 1) {
                    System.out.println("[TCP-client " + (clientId & 0xFF)
                            + "] reconnected after " + attempt + " attempts");
                }
                return;
            } catch (IOException e) {
                last = e;
                System.err.println("[TCP-client " + (clientId & 0xFF)
                        + "] server unavailable (attempt " + attempt + "/"
                        + maxReconnectAttempts + "), retrying in " + reconnectDelayMs + "ms");
                sleep(reconnectDelayMs);
            }
        }
        throw new IOException("Could not connect to " + host + ":" + port
                + " after " + maxReconnectAttempts + " attempts", last);
    }

    private void closeQuietly() {
        try {
            if (socket != null) socket.close();
        } catch (IOException ignored) {
        }
        socket = null;
        in = null;
        out = null;
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public synchronized void close() {
        closeQuietly();
    }
}
