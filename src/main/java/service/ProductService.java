package service;

import db.ProductFilter;
import db.ProductRepository;
import warehouse.Product;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;


public class ProductService {

    private final ProductRepository repository;

    public ProductService(ProductRepository repository) {
        this.repository = repository;
    }

    public Product create(String name, int quantity, int price, String category) {
        validateName(name);
        validateQuantity(quantity);
        validatePrice(price);
        return repository.create(new Product(name, new AtomicInteger(quantity), price, category));
    }

    public Optional<Product> read(int id) {
        return repository.read(id);
    }

    public boolean update(int id, String name, int quantity, int price, String category) {
        validateName(name);
        validateQuantity(quantity);
        validatePrice(price);
        Product product = new Product(name, new AtomicInteger(quantity), price, category);
        product.setId(id);
        return repository.update(product);
    }

    public boolean delete(int id) {
        return repository.delete(id);
    }

    public List<Product> search(ProductFilter filter) {
        return repository.search(filter);
    }

    public int count(ProductFilter filter) {
        return repository.count(filter);
    }

    private void validateName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Product name must not be empty");
        }
    }

    private void validateQuantity(int quantity) {
        if (quantity < 0) {
            throw new IllegalArgumentException("Quantity must not be negative");
        }
    }

    private void validatePrice(int price) {
        if (price < 0) {
            throw new IllegalArgumentException("Price must not be negative");
        }
    }
}
