package warehouse;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public class Warehouse {
    //product name - product
    private final ConcurrentHashMap<String, Product> products
            = new ConcurrentHashMap<>();
    //group name - group
    private final ConcurrentHashMap<String,ProductGroup> productGroups
            = new ConcurrentHashMap<>();

    public int getQuantity(String productName){
        Product productByName = products.get(productName);
        if (productByName == null){
            throw new IllegalArgumentException("Product not found: " + productName);
        }
        return productByName.getQuantity();
    }

    public void reduceProductAmount(String productName, int amount){
        Product productByName = products.get(productName);
        if (productByName == null){
            throw new IllegalArgumentException("Product not found: " + productName);
        }
        if (productByName.getQuantity() < amount) {
            throw new IllegalArgumentException(
                    "Not enough product: " + productName
                            + ", available: " +
                            productByName.getQuantity()
            );
        }
        productByName.reduceQuantity(amount);
    }

    public void addProductAmount(String productName, int amount){
        Product productByName = products.get(productName);
        if (productByName == null){
            throw new IllegalArgumentException("Product not found: " + productName);
        }
        productByName.addQuantity(amount);
    }
    public void addGroup(String groupName, CopyOnWriteArrayList<String> productNames){
        productGroups.put(groupName, new ProductGroup(groupName, productNames));
    }
    public void addProductToGroup(String productName, String groupName){
        ProductGroup group = productGroups.get(groupName);
        if (group == null){
            throw new IllegalArgumentException("Group not found: " + groupName);
        }
        group.getProductNames().add(productName);
    }
    public void setPrice(String productName, int price){
        Product productByName = products.get(productName);
        if (productByName == null){
            throw new IllegalArgumentException("Product not found: " + productName);
        }
        productByName.setPrice(price);
    }

    public void addProduct(Product product){
        products.put(product.getName(), product);
    }


}
