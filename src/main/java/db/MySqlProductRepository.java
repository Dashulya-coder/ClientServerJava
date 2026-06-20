package db;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;


public class MySqlProductRepository extends JdbcProductRepository {

    public MySqlProductRepository(String url, String user, String password) {
        super(open(url, user, password));
    }

    private static Connection open(String url, String user, String password) {
        try {
            return DriverManager.getConnection(url, user, password);
        } catch (SQLException e) {
            throw new RuntimeException("Can't open MySQL DB: " + url, e);
        }
    }

    @Override
    protected String createTableSql() {
        return """
            CREATE TABLE IF NOT EXISTS product (
                id INT PRIMARY KEY AUTO_INCREMENT,
                name VARCHAR(50) NOT NULL,
                quantity INT NOT NULL,
                price INT NOT NULL,
                category VARCHAR(50)
            )
            """;
    }
}
