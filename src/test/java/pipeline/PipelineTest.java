package pipeline;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import warehouse.Product;
import warehouse.Warehouse;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Pipeline tests")
class PipelineTest {

    private static final byte[] TEST_KEY = {
            0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08,
            0x09, 0x0A, 0x0B, 0x0C, 0x0D, 0x0E, 0x0F, 0x10,
            0x11, 0x12, 0x13, 0x14, 0x15, 0x16, 0x17, 0x18,
            0x19, 0x1A, 0x1B, 0x1C, 0x1D, 0x1E, 0x1F, 0x20
    };

    private Warehouse warehouse;
    private Pipeline pipeline;

    @BeforeEach
    void setUp() {
        warehouse = new Warehouse();
        warehouse.addProduct(new Product("Apple", new AtomicInteger(100), 10, "Fruits"));
        warehouse.addProduct(new Product("Banana", new AtomicInteger(50), 5, "Fruits"));
        pipeline = new Pipeline(warehouse, TEST_KEY, 1, 1, 1, 1, 1);
    }

    @AfterEach
    void tearDown() throws InterruptedException {
        pipeline.stop();
    }

    @Test
    @DisplayName("pipeline starts and stops without errors")
    void pipeline_startsAndStops() throws InterruptedException {
        pipeline.start();
        Thread.sleep(100);
    }

    @Test
    @DisplayName("pipeline processes messages for 500ms without crashing")
    void pipeline_processesMessages() throws InterruptedException {
        pipeline.start();
        Thread.sleep(500);
        assertTrue(warehouse.getQuantity("Apple") >= 0);
        assertTrue(warehouse.getQuantity("Banana") >= 0);
    }

    @Test
    @DisplayName("pipeline can be stopped cleanly")
    void pipeline_stopsCleanly() throws InterruptedException {
        pipeline.start();
        Thread.sleep(200);
        assertDoesNotThrow(() -> pipeline.stop());
    }

    @Test
    @DisplayName("warehouse stays consistent after pipeline runs")
    void warehouse_staysConsistent_afterPipeline() throws InterruptedException {
        pipeline.start();
        Thread.sleep(300);
        pipeline.stop();

        assertTrue(warehouse.getQuantity("Apple") >= 0);
        assertTrue(warehouse.getQuantity("Banana") >= 0);
    }

    @Test
    @DisplayName("scaled pipeline: 2 receivers, 2 decryptors, 4 processors, 3 encryptors, 5 senders")
    void pipeline_scaled_startsAndStopsCleanly() throws InterruptedException {
        Warehouse scaledWarehouse = new Warehouse();
        scaledWarehouse.addProduct(new Product("Apple", new AtomicInteger(1000), 10, "Fruits"));
        scaledWarehouse.addProduct(new Product("Banana", new AtomicInteger(1000), 5, "Fruits"));

        Pipeline scaled = new Pipeline(scaledWarehouse, TEST_KEY, 2, 2, 4, 3, 5);
        scaled.start();
        Thread.sleep(500);
        scaled.stop();

        assertTrue(scaledWarehouse.getQuantity("Apple") >= 0);
        assertTrue(scaledWarehouse.getQuantity("Banana") >= 0);
    }
}