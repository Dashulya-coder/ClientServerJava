package demo;

import db.SqliteProductRepository;
import http.JwtService;
import http.StoreHttpServer;
import http.UserService;
import server.StoreServerTCP;
import server.StoreServerUDP;
import service.ProductService;
import warehouse.Warehouse;

public class AllServersDemo {

    private static final int HTTP_PORT = 6002;

    public static void main(String[] args) throws Exception {
        Warehouse warehouse = DemoSupport.sampleWarehouse();
        SqliteProductRepository repository = new SqliteProductRepository("jdbc:sqlite::memory:");
        ProductService productService = new ProductService(repository);

        StoreServerTCP tcp = new StoreServerTCP(
                DemoSupport.TCP_PORT, warehouse, DemoSupport.KEY, productService);
        StoreServerUDP udp = new StoreServerUDP(
                DemoSupport.UDP_PORT, warehouse, DemoSupport.KEY, 0.0, productService);

        UserService userService = new UserService();
        userService.addUser("manager", "manager123");

        StoreHttpServer http = new StoreHttpServer(
                HTTP_PORT, productService, new JwtService("demo-secret"), userService);

        tcp.start();
        udp.start();
        http.start();

        System.out.println("""
                All servers running:
                  TCP  on %d
                  UDP  on %d
                  HTTP on %d  (POST /login  ->  admin/password or manager/manager123)
                Press Ctrl+C to stop.
                """.formatted(DemoSupport.TCP_PORT, DemoSupport.UDP_PORT, HTTP_PORT));

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            tcp.stop();
            udp.stop();
            http.stop();
            repository.close();
        }));

        Thread.currentThread().join();
    }
}
