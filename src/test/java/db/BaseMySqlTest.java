package db;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

public abstract class BaseMySqlTest {

    protected static final MySQLContainer<?> MYSQL =
            new MySQLContainer<>(DockerImageName.parse("mysql:8.0"));

    @BeforeAll
    static void beforeAll() {
        MYSQL.start();
    }

    @AfterAll
    static void afterAll() {
        MYSQL.stop();
    }
}
