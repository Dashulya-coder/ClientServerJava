package db;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import warehouse.Product;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("MySqlProductRepository tests (Testcontainers)")
class MySqlProductRepositoryTest extends BaseMySqlTest {

    private MySqlProductRepository repository;

    private Product product(String name, int qty, int price, String category) {
        return new Product(name, new AtomicInteger(qty), price, category);
    }

    @BeforeEach
    void setUp() {
        repository = new MySqlProductRepository(
                MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
        repository.deleteAll();
        repository.create(product("Apple", 100, 10, "Fruits"));
        repository.create(product("Banana", 50, 5, "Fruits"));
        repository.create(product("Carrot", 200, 3, "Vegetables"));
    }

    @AfterEach
    void tearDown() {
        repository.deleteAll();
        repository.close();
    }

    @Test
    @DisplayName("create assigns id and read returns it")
    void createAndRead() {
        Product created = repository.create(product("Mango", 7, 30, "Fruits"));
        assertNotNull(created.getId());
        assertTrue(repository.read(created.getId()).isPresent());
    }

    @Test
    @DisplayName("update changes fields")
    void update() {
        Product created = repository.create(product("Temp", 1, 1, "X"));
        created.setId(created.getId());
        Product changed = product("Temp", 42, 99, "Y");
        changed.setId(created.getId());
        assertTrue(repository.update(changed));
        assertEquals(42, repository.read(created.getId()).orElseThrow().getQuantity());
    }

    @Test
    @DisplayName("delete removes the product")
    void delete() {
        Product created = repository.create(product("Temp", 1, 1, "X"));
        assertTrue(repository.delete(created.getId()));
        assertTrue(repository.read(created.getId()).isEmpty());
    }

    @Test
    @DisplayName("dynamic filter + pagination")
    void searchWithFilterAndPaging() {
        List<Product> fruits = repository.search(
                ProductFilter.builder().category("Fruits").build());
        assertEquals(2, fruits.size());

        List<Product> cheap = repository.search(
                ProductFilter.builder().maxPrice(5).build());
        assertEquals(2, cheap.size());

        List<Product> firstPage = repository.search(
                ProductFilter.builder().size(2).page(0).build());
        assertEquals(2, firstPage.size());
        assertEquals(3, repository.count(ProductFilter.builder().build()));
    }
}
