package db;

import warehouse.Product;

import java.util.List;
import java.util.Optional;

// data-access layer for products
public interface ProductRepository {

    // returns product with generated id
    Product create(Product product);

    Optional<Product> read(int id);

    // edit by id
    boolean update(Product product);

    boolean delete(int id);

    List<Product> search(ProductFilter filter);

    // total matches
    int count(ProductFilter filter);

    int deleteAll();
}

//для пагінації треба ще зробити в репо size i page