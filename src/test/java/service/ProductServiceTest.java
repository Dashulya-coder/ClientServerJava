package service;

import db.ProductFilter;
import db.SqliteProductRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import warehouse.Product;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ProductService tests (SQLite)")
class ProductServiceTest {

    private SqliteProductRepository repository;
    private ProductService service;

    @BeforeEach
    void setUp() {
        repository = new SqliteProductRepository("jdbc:sqlite::memory:");
        service = new ProductService(repository);
        // seed data
        service.create("Apple", 100, 10, "Fruits");
        service.create("Banana", 50, 5, "Fruits");
        service.create("Pineapple", 10, 25, "Fruits");
        service.create("Carrot", 200, 3, "Vegetables");
        service.create("Potato", 500, 2, "Vegetables");
    }

    @AfterEach
    void tearDown() {
        repository.close();
    }

    @Nested
    @DisplayName("CRUD")
    class Crud {

        @Test
        @DisplayName("create assigns an id and read returns the product")
        void create_andRead() {
            Product created = service.create("Mango", 7, 30, "Fruits");
            assertNotNull(created.getId());

            Optional<Product> found = service.read(created.getId());
            assertTrue(found.isPresent());
            assertEquals("Mango", found.get().getName());
            assertEquals(7, found.get().getQuantity());
            assertEquals(30, found.get().getPrice());
            assertEquals("Fruits", found.get().getGroup());
        }

        @Test
        @DisplayName("read of unknown id returns empty")
        void read_unknown_empty() {
            assertTrue(service.read(999999).isEmpty());
        }

        @Test
        @DisplayName("update changes fields")
        void update_changesFields() {
            Product apple = service.create("Temp", 1, 1, "X");
            boolean ok = service.update(apple.getId(), "Temp", 42, 99, "Y");
            assertTrue(ok);

            Product updated = service.read(apple.getId()).orElseThrow();
            assertEquals(42, updated.getQuantity());
            assertEquals(99, updated.getPrice());
            assertEquals("Y", updated.getGroup());
        }

        @Test
        @DisplayName("update of unknown id returns false")
        void update_unknown_false() {
            assertFalse(service.update(999999, "Ghost", 1, 1, "Z"));
        }

        @Test
        @DisplayName("delete removes the product")
        void delete_removes() {
            Product p = service.create("Temp", 1, 1, "X");
            assertTrue(service.delete(p.getId()));
            assertTrue(service.read(p.getId()).isEmpty());
        }

        @Test
        @DisplayName("delete of unknown id returns false")
        void delete_unknown_false() {
            assertFalse(service.delete(999999));
        }
    }

    @Nested
    @DisplayName("Validation")
    class Validation {

        @Test
        @DisplayName("blank name is rejected")
        void blankName_rejected() {
            assertThrows(IllegalArgumentException.class, () -> service.create(" ", 1, 1, "X"));
        }

        @Test
        @DisplayName("negative quantity/price are rejected")
        void negatives_rejected() {
            assertThrows(IllegalArgumentException.class, () -> service.create("A", -1, 1, "X"));
            assertThrows(IllegalArgumentException.class, () -> service.create("A", 1, -1, "X"));
        }
    }

    @Nested
    @DisplayName("Dynamic filtered search")
    class Search {

        @Test
        @DisplayName("no filter returns everything")
        void noFilter_returnsAll() {
            assertEquals(5, service.search(ProductFilter.builder().build()).size());
        }

        @Test
        @DisplayName("by name substring")
        void byName() {
            List<Product> r = service.search(ProductFilter.builder().name("apple").build());
            assertEquals(2, r.size());
        }

        @Test
        @DisplayName("by category only")
        void byCategory() {
            List<Product> r = service.search(ProductFilter.builder().category("Vegetables").build());
            assertEquals(2, r.size());
            assertTrue(r.stream().allMatch(p -> p.getGroup().equals("Vegetables")));
        }

        @Test
        @DisplayName("by price greater than 3 (only minPrice)")
        void byMinPriceOnly() {
            List<Product> r = service.search(ProductFilter.builder().minPrice(4).build());
            assertEquals(3, r.size());
            assertTrue(r.stream().allMatch(p -> p.getPrice() >= 4));
        }

        @Test
        @DisplayName("by price range")
        void byPriceRange() {
            List<Product> r = service.search(
                    ProductFilter.builder().minPrice(3).maxPrice(10).build());
            assertEquals(3, r.size());
        }

        @Test
        @DisplayName("by quantity range")
        void byQuantityRange() {
            List<Product> r = service.search(
                    ProductFilter.builder().minQuantity(100).maxQuantity(200).build());
            assertEquals(2, r.size());
        }

        @Test
        @DisplayName("combined name + category")
        void byNameAndCategory() {
            List<Product> r = service.search(
                    ProductFilter.builder().name("a").category("Fruits").build());
            assertEquals(3, r.size());
            assertTrue(r.stream().allMatch(p -> p.getGroup().equals("Fruits")));
        }

        @Test
        @DisplayName("combined category + price range")
        void byCategoryAndPrice() {
            List<Product> r = service.search(ProductFilter.builder()
                    .category("Fruits").maxPrice(10).build());
            assertEquals(2, r.size());
        }
    }

    @Nested
    @DisplayName("Pagination")
    class Pagination {

        @Test
        @DisplayName("size limits the page; count returns total ignoring paging")
        void pageSize() {
            ProductFilter page0 = ProductFilter.builder().size(2).page(0).build();
            ProductFilter page1 = ProductFilter.builder().size(2).page(1).build();
            ProductFilter page2 = ProductFilter.builder().size(2).page(2).build();

            assertEquals(2, service.search(page0).size());
            assertEquals(2, service.search(page1).size());
            assertEquals(1, service.search(page2).size());
            assertEquals(5, service.count(ProductFilter.builder().build()));
        }

        @Test
        @DisplayName("pages do not overlap")
        void pages_noOverlap() {
            List<Product> page0 = service.search(ProductFilter.builder().size(2).page(0).build());
            List<Product> page1 = service.search(ProductFilter.builder().size(2).page(1).build());
            assertNotEquals(page0.get(0).getId(), page1.get(0).getId());
        }

        @Test
        @DisplayName("pagination combined with a filter")
        void pagination_withFilter() {
            ProductFilter f = ProductFilter.builder().category("Fruits").size(2).page(0).build();
            assertEquals(2, service.search(f).size());
            assertEquals(3, service.count(ProductFilter.builder().category("Fruits").build()));
        }
    }
}
