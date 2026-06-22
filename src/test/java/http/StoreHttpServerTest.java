package http;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import db.SqliteProductRepository;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import service.ProductService;

import java.io.IOException;
import java.util.Date;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

@DisplayName("StoreHttpServer REST API tests")
class StoreHttpServerTest {

    private SqliteProductRepository repository;
    private StoreHttpServer server;
    private String token;

    @BeforeEach
    void setUp() throws IOException {
        repository = new SqliteProductRepository("jdbc:sqlite::memory:");
        ProductService productService = new ProductService(repository);
        JwtService jwtService = new JwtService("test-secret");
        UserService userService = new UserService();

        server = new StoreHttpServer(0, productService, jwtService, userService);
        server.start();

        RestAssured.baseURI = "http://localhost";
        RestAssured.port = server.getPort();
        RestAssured.enableLoggingOfRequestAndResponseIfValidationFails();

        token = login("admin", "password");
    }

    @AfterEach
    void tearDown() {
        server.stop();
        repository.close();
    }

    private String login(String username, String password) {
        return given()
                .contentType(ContentType.JSON)
                .body(Map.of("username", username, "password", password))
                .when()
                .post("/login")
                .then()
                .extract().path("token");
    }

    private int createProduct(String name, int qty, int price, String category) {
        return given()
                .header("Authorization", "Bearer " + token)
                .contentType(ContentType.JSON)
                .body(Map.of("name", name, "quantity", qty, "price", price, "category", category))
                .when()
                .put("/products")
                .then()
                .statusCode(201)
                .extract().path("id");
    }

    @Test
    @DisplayName("POST /login returns a token for valid credentials")
    void login_valid_returnsToken() {
        given()
                .contentType(ContentType.JSON)
                .body(Map.of("username", "admin", "password", "password"))
                .expect()
                .statusCode(200)
                .body("token", notNullValue())
                .when()
                .post("/login");
    }

    @Test
    @DisplayName("POST /login returns 401 for invalid credentials")
    void login_invalid_returns401() {
        given()
                .contentType(ContentType.JSON)
                .body(Map.of("username", "admin", "password", "wrong"))
                .expect()
                .statusCode(401)
                .when()
                .post("/login");
    }

    @Test
    @DisplayName("protected endpoint without token returns 401")
    void products_withoutToken_returns401() {
        given()
                .expect()
                .statusCode(401)
                .when()
                .get("/products/1");
    }

    @Test
    @DisplayName("protected endpoint with a forged token returns 401")
    void products_withForgedToken_returns401() {
        given()
                .header("Authorization", "Bearer not-a-real-jwt")
                .expect()
                .statusCode(401)
                .when()
                .get("/products/1");
    }

    @Test
    @DisplayName("protected endpoint with a token signed by a wrong secret returns 401")
    void products_withWrongSecretToken_returns401() {
        String wrongToken = JWT.create()
                .withSubject("admin")
                .withExpiresAt(new Date(System.currentTimeMillis() + 3600_000))
                .sign(Algorithm.HMAC256("some-other-secret"));

        given()
                .header("Authorization", "Bearer " + wrongToken)
                .expect()
                .statusCode(401)
                .when()
                .get("/products/1");
    }

    @Test
    @DisplayName("protected endpoint with an expired token returns 401")
    void products_withExpiredToken_returns401() {
        String expiredToken = JWT.create()
                .withSubject("admin")
                .withExpiresAt(new Date(System.currentTimeMillis() - 1000))
                .sign(Algorithm.HMAC256("test-secret"));

        given()
                .header("Authorization", "Bearer " + expiredToken)
                .expect()
                .statusCode(401)
                .when()
                .get("/products/1");
    }

    @Test
    @DisplayName("PUT /products creates a product (201) and GET returns it")
    void create_thenGet() {
        int id = createProduct("Apple", 100, 10, "Fruits");

        given()
                .header("Authorization", "Bearer " + token)
                .expect()
                .statusCode(200)
                .body("id", is(id))
                .body("name", is("Apple"))
                .body("quantity", is(100))
                .body("category", is("Fruits"))
                .when()
                .get("/products/" + id);
    }

    @Test
    @DisplayName("PUT /products with a duplicate name returns 409")
    void create_duplicate_returns409() {
        createProduct("Banana", 50, 5, "Fruits");

        given()
                .header("Authorization", "Bearer " + token)
                .contentType(ContentType.JSON)
                .body(Map.of("name", "Banana", "quantity", 1, "price", 1, "category", "Fruits"))
                .expect()
                .statusCode(409)
                .when()
                .put("/products");
    }

    @Test
    @DisplayName("GET /products/{id} returns 404 for unknown id")
    void get_unknown_returns404() {
        given()
                .header("Authorization", "Bearer " + token)
                .expect()
                .statusCode(404)
                .when()
                .get("/products/999999");
    }

    @Test
    @DisplayName("POST /products/{id} updates an existing product")
    void update_existing() {
        int id = createProduct("Carrot", 200, 3, "Vegetables");

        given()
                .header("Authorization", "Bearer " + token)
                .contentType(ContentType.JSON)
                .body(Map.of("name", "Carrot", "quantity", 222, "price", 4, "category", "Vegetables"))
                .expect()
                .statusCode(200)
                .body("quantity", is(222))
                .body("price", is(4))
                .when()
                .post("/products/" + id);
    }

    @Test
    @DisplayName("POST /products/{id} returns 404 for unknown id")
    void update_unknown_returns404() {
        given()
                .header("Authorization", "Bearer " + token)
                .contentType(ContentType.JSON)
                .body(Map.of("name", "Ghost", "quantity", 1, "price", 1, "category", "X"))
                .expect()
                .statusCode(404)
                .when()
                .post("/products/999999");
    }

    @Test
    @DisplayName("DELETE /products/{id} removes an existing product")
    void delete_existing() {
        int id = createProduct("Potato", 500, 2, "Vegetables");

        given()
                .header("Authorization", "Bearer " + token)
                .expect()
                .statusCode(200)
                .when()
                .delete("/products/" + id);

        given()
                .header("Authorization", "Bearer " + token)
                .expect()
                .statusCode(404)
                .when()
                .get("/products/" + id);
    }

    @Test
    @DisplayName("DELETE /products/{id} returns 404 for unknown id")
    void delete_unknown_returns404() {
        given()
                .header("Authorization", "Bearer " + token)
                .expect()
                .statusCode(404)
                .when()
                .delete("/products/999999");
    }
}
