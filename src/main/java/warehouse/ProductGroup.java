package warehouse;

import java.util.concurrent.CopyOnWriteArrayList;

public class ProductGroup {
    private final String name;
    private CopyOnWriteArrayList<String> productNames;
    public ProductGroup(String name, CopyOnWriteArrayList<String> productNames) {
        this.name = name;
        this.productNames = productNames;
    }
    public String getName(){
        return name;
    }
    public CopyOnWriteArrayList<String> getProductNames(){
        return productNames;
    }

}
