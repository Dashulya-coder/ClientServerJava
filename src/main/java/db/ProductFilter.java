package db;


public class ProductFilter {

    private final String name; // substring match
    private final String category; // exact match
    private final Integer minQuantity;
    private final Integer maxQuantity;
    private final Integer minPrice;
    private final Integer maxPrice;
    private final Integer page; // page index
    private final Integer size; // page size

    private ProductFilter(Builder b) {
        this.name = b.name;
        this.category = b.category;
        this.minQuantity = b.minQuantity;
        this.maxQuantity = b.maxQuantity;
        this.minPrice = b.minPrice;
        this.maxPrice = b.maxPrice;
        this.page = b.page;
        this.size = b.size;
    }

    public static Builder builder() {
        return new Builder();
    }

    public String getName() { return name; }
    public String getCategory() { return category; }
    public Integer getMinQuantity() { return minQuantity; }
    public Integer getMaxQuantity() { return maxQuantity; }
    public Integer getMinPrice() { return minPrice; }
    public Integer getMaxPrice() { return maxPrice; }
    public Integer getPage() { return page; }
    public Integer getSize() { return size; }

    public static class Builder {
        private String name;
        private String category;
        private Integer minQuantity;
        private Integer maxQuantity;
        private Integer minPrice;
        private Integer maxPrice;
        private Integer page;
        private Integer size;

        public Builder name(String name) { this.name = name; return this; }
        public Builder category(String category) { this.category = category; return this; }
        public Builder minQuantity(Integer v) { this.minQuantity = v; return this; }
        public Builder maxQuantity(Integer v) { this.maxQuantity = v; return this; }
        public Builder minPrice(Integer v) { this.minPrice = v; return this; }
        public Builder maxPrice(Integer v) { this.maxPrice = v; return this; }
        public Builder page(Integer page) { this.page = page; return this; }
        public Builder size(Integer size) { this.size = size; return this; }

        public ProductFilter build() {

            return new ProductFilter(this);
        }
    }
}
