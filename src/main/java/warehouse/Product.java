package warehouse;

import java.util.concurrent.atomic.AtomicInteger;

public class Product {

    private Integer id;  //primary key
    private final String name;
    private AtomicInteger quantity;
    private volatile int price;
    private final String group;

    public Product(String name, AtomicInteger quantity, int price, String group) {
        this.name = name;
        this.quantity = quantity;
        this.price = price;
        this.group = group;
    }
    public Integer getId(){
        return id;
    }
    public void setId(Integer id){
        this.id = id;
    }
    public String getName(){
        return name;
    }
    public int getPrice(){
        return price;
    }
    public void setPrice(int price){
        this.price = price;
    }

    public String getGroup(){
        return group;
    }

    public int getQuantity(){
        return quantity.get();
    }

    public void addQuantity(int amount){
        quantity.addAndGet(amount);
    }
    public void reduceQuantity(int amount){
        quantity.addAndGet(-amount);
    }

}
