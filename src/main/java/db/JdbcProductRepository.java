package db;

import warehouse.Product;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

// Shared JDBC logic. Subclasses only supply the connection and CREATE TABLE.
public abstract class JdbcProductRepository implements ProductRepository, AutoCloseable {

    protected final Connection connection;

    protected JdbcProductRepository(Connection connection) {
        this.connection = connection;
        init();
    }

    protected abstract String createTableSql();

    private void init() {
        try (Statement st = connection.createStatement()) {
            st.execute(createTableSql());
        } catch (SQLException e) {
            throw new RuntimeException("Exception while DB init", e);
        }
    }

    @Override
    public synchronized Product create(Product product) {
        String sql = "INSERT INTO product(name, quantity, price, category) VALUES (?, ?, ?, ?)";
        try (PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, product.getName());
            ps.setInt(2, product.getQuantity());
            ps.setInt(3, product.getPrice());
            ps.setString(4, product.getGroup());
            if (ps.executeUpdate() < 1) {
                throw new RuntimeException("Insert failed");
            }
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    product.setId(keys.getInt(1));
                }
            }
            return product;
        } catch (SQLException e) {
            throw new RuntimeException("Can't insert product: " + product.getName(), e);
        }
    }

    // methods are synchronized so a single shared connection is used safely
    @Override
    public synchronized Optional<Product> read(int id) {
        try (PreparedStatement ps = connection.prepareStatement("SELECT * FROM product WHERE id = ?")) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(map(rs));
                }
            }
            return Optional.empty();
        } catch (SQLException e) {
            throw new RuntimeException("Can't read product id: " + id, e);
        }
    }

    @Override
    public synchronized boolean update(Product product) {
        if (product.getId() == null) {
            throw new IllegalArgumentException("Product id is required for update");
        }
        String sql = "UPDATE product SET name = ?, quantity = ?, price = ?, category = ? WHERE id = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, product.getName());
            ps.setInt(2, product.getQuantity());
            ps.setInt(3, product.getPrice());
            ps.setString(4, product.getGroup());
            ps.setInt(5, product.getId());
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new RuntimeException("Can't update product id: " + product.getId(), e);
        }
    }

    @Override
    public synchronized boolean delete(int id) {
        try (PreparedStatement ps = connection.prepareStatement("DELETE FROM product WHERE id = ?")) {
            ps.setInt(1, id);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new RuntimeException("Can't delete product id: " + id, e);
        }
    }

    @Override
    public synchronized List<Product> search(ProductFilter filter) {
        List<Object> params = new ArrayList<>();
        StringBuilder sql = new StringBuilder("SELECT * FROM product");
        appendWhere(sql, filter, params);
        sql.append(" ORDER BY id");

        if (filter.getSize() != null) {
            sql.append(" LIMIT ? OFFSET ?");
            params.add(filter.getSize());
            int page = filter.getPage() != null ? filter.getPage() : 0;
            params.add(page * filter.getSize());
        }

        try (PreparedStatement ps = connection.prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++) {
                ps.setObject(i + 1, params.get(i));
            }
            List<Product> result = new ArrayList<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(map(rs));
                }
            }
            return result;
        } catch (SQLException e) {
            throw new RuntimeException("Can't search products", e);
        }
    }

    @Override
    public synchronized int count(ProductFilter filter) {
        List<Object> params = new ArrayList<>();
        StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM product");
        appendWhere(sql, filter, params);
        try (PreparedStatement ps = connection.prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++) {
                ps.setObject(i + 1, params.get(i));
            }
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Can't count products", e);
        }
    }

    @Override
    public synchronized int deleteAll() {
        try (Statement st = connection.createStatement()) {
            return st.executeUpdate("DELETE FROM product");
        } catch (SQLException e) {
            throw new RuntimeException("Can't delete all products", e);
        }
    }

    private void appendWhere(StringBuilder sql, ProductFilter f, List<Object> params) {
        List<String> conditions = new ArrayList<>();
        if (f.getName() != null) {
            conditions.add("name LIKE ?");
            params.add("%" + f.getName() + "%");
        }
        if (f.getCategory() != null) {
            conditions.add("category = ?");
            params.add(f.getCategory());
        }
        if (f.getMinQuantity() != null) {
            conditions.add("quantity >= ?");
            params.add(f.getMinQuantity());
        }
        if (f.getMaxQuantity() != null) {
            conditions.add("quantity <= ?");
            params.add(f.getMaxQuantity());
        }
        if (f.getMinPrice() != null) {
            conditions.add("price >= ?");
            params.add(f.getMinPrice());
        }
        if (f.getMaxPrice() != null) {
            conditions.add("price <= ?");
            params.add(f.getMaxPrice());
        }
        if (!conditions.isEmpty()) {
            sql.append(" WHERE ").append(String.join(" AND ", conditions));
        }
    }

    private Product map(ResultSet rs) throws SQLException {
        Product p = new Product(
                rs.getString("name"),
                new AtomicInteger(rs.getInt("quantity")),
                rs.getInt("price"),
                rs.getString("category"));
        p.setId(rs.getInt("id"));
        return p;
    }

    @Override
    public void close() {
        try {
            connection.close();
        } catch (SQLException ignored) {
        }
    }
}
