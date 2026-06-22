package http;

import com.fasterxml.jackson.databind.JsonNode;
import com.sun.net.httpserver.HttpContext;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import service.ProductService;
import warehouse.Product;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

public class StoreHttpServer {

    private static final String PRODUCTS = "/products";

    private final HttpServer server;
    private final ProductService productService;
    private final JwtService jwtService;
    private final UserService userService;

    public StoreHttpServer(int port, ProductService productService, JwtService jwtService,
                           UserService userService) throws IOException {
        this.productService = productService;
        this.jwtService = jwtService;
        this.userService = userService;
        this.server = HttpServer.create(new InetSocketAddress(port), 0);
        createEndpoints();
    }

    private void createEndpoints() {
        server.createContext("/login", this::handleLogin);

        HttpContext products = server.createContext(PRODUCTS, this::handleProducts);
        products.setAuthenticator(new BearerAuth(jwtService));
    }

    public void start() {
        server.start();
        System.out.println("[HTTP] server listening on port " + getPort());
    }

    public void stop() {
        server.stop(0);
        System.out.println("[HTTP] server stopped");
    }

    public int getPort() {
        return server.getAddress().getPort();
    }

    private void handleLogin(HttpExchange exchange) throws IOException {
        try {
            if (!exchange.getRequestMethod().equals("POST")) {
                send(exchange, 405, null);
                return;
            }
            JsonNode body = JsonHelper.parse(readBody(exchange));
            String username = body.path("username").asText(null);
            String password = body.path("password").asText(null);

            if (!userService.isValid(username, password)) {
                send(exchange, 401, error("invalid credentials"));
                return;
            }
            String token = jwtService.issueToken(username);
            send(exchange, 200, JsonHelper.write(Map.of("token", token)));
        } catch (Exception e) {
            send(exchange, 400, error("bad request"));
        }
    }

    private void handleProducts(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod();
        String path = exchange.getRequestURI().getPath();
        String rest = path.substring(PRODUCTS.length());

        try {
            Integer id = parseId(rest);

            if (method.equals("GET") && id != null) {
                getProduct(exchange, id);
            } else if (method.equals("PUT") && id == null) {
                createProduct(exchange);
            } else if (method.equals("POST") && id != null) {
                updateProduct(exchange, id);
            } else if (method.equals("DELETE") && id != null) {
                deleteProduct(exchange, id);
            } else {
                send(exchange, 405, error("method not allowed"));
            }
        } catch (NumberFormatException e) {
            send(exchange, 400, error("invalid id"));
        } catch (IllegalArgumentException e) {
            send(exchange, 400, error(e.getMessage()));
        } catch (Exception e) {
            send(exchange, 500, error("internal error"));
        }
    }

    private void getProduct(HttpExchange exchange, int id) throws IOException {
        Optional<Product> found = productService.read(id);
        if (found.isPresent()) {
            send(exchange, 200, JsonHelper.write(toMap(found.get())));
        } else {
            send(exchange, 404, error("not found"));
        }
    }

    private void createProduct(HttpExchange exchange) throws IOException {
        JsonNode body = JsonHelper.parse(readBody(exchange));
        String name = body.path("name").asText(null);
        if (name == null || name.isBlank()) {
            send(exchange, 400, error("name is required"));
            return;
        }
        if (productService.findByName(name).isPresent()) {
            send(exchange, 409, error("product already exists"));
            return;
        }
        Product created = productService.create(
                name,
                body.path("quantity").asInt(0),
                body.path("price").asInt(0),
                body.path("category").asText(null));
        send(exchange, 201, JsonHelper.write(toMap(created)));
    }

    private void updateProduct(HttpExchange exchange, int id) throws IOException {
        JsonNode body = JsonHelper.parse(readBody(exchange));
        boolean updated = productService.update(
                id,
                body.path("name").asText(null),
                body.path("quantity").asInt(0),
                body.path("price").asInt(0),
                body.path("category").asText(null));
        if (updated) {
            send(exchange, 200, JsonHelper.write(toMap(productService.read(id).orElseThrow())));
        } else {
            send(exchange, 404, error("not found"));
        }
    }

    private void deleteProduct(HttpExchange exchange, int id) throws IOException {
        if (productService.delete(id)) {
            send(exchange, 200, JsonHelper.write(Map.of("deleted", id)));
        } else {
            send(exchange, 404, error("not found"));
        }
    }

    private Integer parseId(String rest) {
        if (rest.isEmpty() || rest.equals("/")) {
            return null;
        }
        return Integer.parseInt(rest.substring(1));
    }

    private Map<String, Object> toMap(Product p) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", p.getId());
        m.put("name", p.getName());
        m.put("quantity", p.getQuantity());
        m.put("price", p.getPrice());
        m.put("category", p.getGroup());
        return m;
    }

    private String error(String message) {
        return JsonHelper.write(Map.of("error", message == null ? "error" : message));
    }

    private String readBody(HttpExchange exchange) throws IOException {
        return new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
    }

    private void send(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body == null ? new byte[0] : body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        if (bytes.length == 0) {
            exchange.sendResponseHeaders(status, -1);
        } else {
            exchange.sendResponseHeaders(status, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        }
        exchange.close();
    }
}
