package db;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class SqliteProductRepository extends JdbcProductRepository {

    public SqliteProductRepository(String url) {
        super(open(url));
    }

    private static Connection open(String url) {
        try {
            return DriverManager.getConnection(url);
        } catch (SQLException e) {
            throw new RuntimeException("Can't open SQLite DB: " + url, e);
        }
    }

    @Override
    protected String createTableSql() {
        return """
            CREATE TABLE IF NOT EXISTS product (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name VARCHAR(50) NOT NULL,
                quantity INTEGER NOT NULL,
                price INTEGER NOT NULL,
                category VARCHAR(50)
            )
            """;
    }
}
