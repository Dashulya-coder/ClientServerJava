package server;

import protocol.CryptoService;
import protocol.Message;
import protocol.Package;
import protocol.PackageBuilder;
import protocol.PackageParser;
import warehouse.Warehouse;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

// TCP server accepts multiple clients, each served on its own thread
// a disconnecting client only tears down its own thread so the server keeps running
public class StoreServerTCP {

    private final int requestedPort;
    private final RequestHandler handler;
    private final PackageBuilder builder;
    private final PackageParser parser;

    private volatile boolean running = false;
    private ServerSocket serverSocket;
    private ExecutorService clientPool;
    private Thread acceptThread;

    public StoreServerTCP(int port, Warehouse warehouse, byte[] key) {
        this(port, warehouse, key, null);
    }

    public StoreServerTCP(int port, Warehouse warehouse, byte[] key,
                          service.ProductService productService) {
        this.requestedPort = port;
        this.handler = new RequestHandler(warehouse, productService);
        CryptoService crypto = new CryptoService(key);
        this.builder = new PackageBuilder(crypto);
        this.parser = new PackageParser(crypto);
    }

    public void start() throws IOException {
        serverSocket = new ServerSocket();
        serverSocket.setReuseAddress(true); // allow reusing the same port after restart
        serverSocket.bind(new java.net.InetSocketAddress(requestedPort));
        clientPool = Executors.newCachedThreadPool();
        running = true;

        acceptThread = new Thread(this::acceptLoop, "tcp-accept");
        acceptThread.start();
        System.out.println("[TCP] server listening on port " + getPort());
    }

    public int getPort() {
        return serverSocket != null ? serverSocket.getLocalPort() : requestedPort;
    }

    private void acceptLoop() {
        while (running) {
            try {
                Socket client = serverSocket.accept();
                clientPool.submit(() -> serveClient(client));
            } catch (IOException e) {
                if (running) {
                    System.err.println("[TCP] accept failed: " + e.getMessage());
                }
            }
        }
    }

    private void serveClient(Socket socket) {
        String who = socket.getRemoteSocketAddress().toString();
        System.out.println("[TCP] client connected: " + who);
        try (socket;
             DataInputStream in = new DataInputStream(socket.getInputStream());
             DataOutputStream out = new DataOutputStream(socket.getOutputStream())) {

            while (running) {
                byte[] raw = PacketReader.readPacket(in);
                try {
                    Package request = parser.parse(raw);
                    Message response = handler.handle(request.getMessage());
                    byte[] reply = builder.build(
                            request.getClientId(), request.getPacketId(), response);
                    out.write(reply);
                    out.flush();
                } catch (Exception e) {
                    // bad packet -  log but keep the connection open
                    System.err.println("[TCP] failed to process packet from " + who
                            + ": " + e.getMessage());
                }
            }
        } catch (EOFException e) {
            System.out.println("[TCP] client disconnected: " + who);
        } catch (IOException e) {
            System.out.println("[TCP] connection lost with " + who + ": " + e.getMessage());
        }
    }

    public void stop() {
        running = false;
        try {
            if (serverSocket != null) serverSocket.close();
        } catch (IOException ignored) {
        }
        if (clientPool != null) {
            clientPool.shutdownNow();
            try {
                clientPool.awaitTermination(2, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        System.out.println("[TCP] server stopped");
    }
}
