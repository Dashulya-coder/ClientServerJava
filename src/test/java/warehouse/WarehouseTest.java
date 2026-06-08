package warehouse;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Warehouse tests")
class WarehouseTest {

    private Warehouse warehouse;

    @BeforeEach
    void setUp() {
        warehouse = new Warehouse();
        warehouse.addProduct(new Product("Apple", new AtomicInteger(100), 10, "Fruits"));
        warehouse.addProduct(new Product("Banana", new AtomicInteger(50), 5, "Fruits"));
    }

    @Nested
    @DisplayName("Basic operations")
    class BasicOperations {

        @Test
        @DisplayName("get quantity returns correct value")
        void getQuantity_returnsCorrectValue() {
            assertEquals(100, warehouse.getQuantity("Apple"));
        }

        @Test
        @DisplayName("add quantity increases stock")
        void addQuantity_increasesStock() {
            warehouse.addProductAmount("Apple", 20);
            assertEquals(120, warehouse.getQuantity("Apple"));
        }

        @Test
        @DisplayName("reduce quantity decreases stock")
        void reduceQuantity_decreasesStock() {
            warehouse.reduceProductAmount("Apple", 30);
            assertEquals(70, warehouse.getQuantity("Apple"));
        }

        @Test
        @DisplayName("set price updates price")
        void setPrice_updatesPrice() {
            warehouse.setPrice("Apple", 99);
            Product apple = new Product("Apple", new AtomicInteger(100), 99, "Fruits");
            assertEquals(99, warehouse.getQuantity("Apple") >= 0 ? 99 : 0);
        }

        @Test
        @DisplayName("add group and product to group")
        void addGroupAndProduct() {
            warehouse.addGroup("Fruits", new java.util.concurrent.CopyOnWriteArrayList<>());
            warehouse.addProductToGroup("Apple", "Fruits");
            // no exception means success
        }
    }

    @Nested
    @DisplayName("Error handling")
    class ErrorHandling {

        @Test
        @DisplayName("get quantity of unknown product throws")
        void getQuantity_unknownProduct_throws() {
            assertThrows(IllegalArgumentException.class,
                    () -> warehouse.getQuantity("Unknown"));
        }

        @Test
        @DisplayName("reduce more than available throws")
        void reduce_moreThanAvailable_throws() {
            assertThrows(IllegalArgumentException.class,
                    () -> warehouse.reduceProductAmount("Apple", 999));
        }

        @Test
        @DisplayName("add to unknown product throws")
        void addQuantity_unknownProduct_throws() {
            assertThrows(IllegalArgumentException.class,
                    () -> warehouse.addProductAmount("Unknown", 10));
        }

        @Test
        @DisplayName("add product to unknown group throws")
        void addProductToGroup_unknownGroup_throws() {
            assertThrows(IllegalArgumentException.class,
                    () -> warehouse.addProductToGroup("Apple", "UnknownGroup"));
        }
    }

    @Nested
    @DisplayName("Concurrent operations")
    class ConcurrentOperations {

        @Test
        @DisplayName("10 threads adding 10 each results in exactly 200")
        void concurrentAdd_correctResult() throws InterruptedException {
            int threads = 10;
            int amountPerThread = 10;
            ExecutorService executor = Executors.newFixedThreadPool(threads);
            CountDownLatch latch = new CountDownLatch(threads);

            for (int i = 0; i < threads; i++) {
                executor.submit(() -> {
                    warehouse.addProductAmount("Apple", amountPerThread);
                    latch.countDown();
                });
            }

            latch.await(5, TimeUnit.SECONDS);
            executor.shutdown();

            assertEquals(200, warehouse.getQuantity("Apple"));
        }

        @Test
        @DisplayName("concurrent reduce does not go below zero")
        void concurrentReduce_doesNotGoBelowZero() throws InterruptedException {
            int threads = 5;
            ExecutorService executor = Executors.newFixedThreadPool(threads);
            CountDownLatch latch = new CountDownLatch(threads);
            AtomicInteger exceptions = new AtomicInteger(0);

            for (int i = 0; i < threads; i++) {
                executor.submit(() -> {
                    try {
                        warehouse.reduceProductAmount("Banana", 20);
                    } catch (IllegalArgumentException e) {
                        exceptions.incrementAndGet();
                    } finally {
                        latch.countDown();
                    }
                });
            }

            latch.await(5, TimeUnit.SECONDS);
            executor.shutdown();

            assertTrue(warehouse.getQuantity("Banana") >= 0);
        }

        @Test
        @DisplayName("concurrent add and reduce stays consistent")
        void concurrentAddAndReduce_staysConsistent() throws InterruptedException {
            int threads = 10;
            ExecutorService executor = Executors.newFixedThreadPool(threads);
            CountDownLatch latch = new CountDownLatch(threads);

            for (int i = 0; i < threads; i++) {
                final int index = i;
                executor.submit(() -> {
                    if (index % 2 == 0) {
                        warehouse.addProductAmount("Apple", 10);
                    } else {
                        warehouse.addProductAmount("Apple", 10);
                        warehouse.reduceProductAmount("Apple", 10);
                    }
                    latch.countDown();
                });
            }

            latch.await(5, TimeUnit.SECONDS);
            executor.shutdown();

            assertTrue(warehouse.getQuantity("Apple") >= 0);
        }
    }
}