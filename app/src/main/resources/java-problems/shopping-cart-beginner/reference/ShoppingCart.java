package jp.yuya.dev.developerdungeon.javaproblems.shopping.cart.beginner;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class ShoppingCart {
    private final List<CartItem> items;

    public ShoppingCart() { this.items = new ArrayList<>(); }
    public List<CartItem> getItems() { return List.copyOf(items); }
    public void add(Product product, int quantity) {
        Objects.requireNonNull(product);
        if (quantity <= 0) throw new IllegalArgumentException("数量は1以上です");
        items.stream().filter(item -> item.getProduct().getId().equals(product.getId())).findFirst()
                .ifPresentOrElse(item -> item.increaseQuantity(quantity), () -> items.add(new CartItem(product, quantity)));
    }
    public int totalQuantity() { return items.stream().mapToInt(CartItem::getQuantity).sum(); }
    public int totalPriceYen() { return items.stream().mapToInt(CartItem::subtotal).sum(); }
    public void clear() { items.clear(); }
}

final class Product {
    private final String id;
    private final String name;
    private final int unitPriceYen;
    Product(String id, String name, int unitPriceYen) {
        if (id == null || id.isBlank() || name == null || name.isBlank()) throw new IllegalArgumentException("商品情報が必要です");
        if (unitPriceYen <= 0) throw new IllegalArgumentException("単価は1円以上です");
        this.id = id; this.name = name; this.unitPriceYen = unitPriceYen;
    }
    String getId() { return id; }
    String getName() { return name; }
    int getUnitPriceYen() { return unitPriceYen; }
}

final class CartItem {
    private final Product product;
    private int quantity;
    CartItem(Product product, int quantity) { this.product = Objects.requireNonNull(product); increaseQuantity(quantity); }
    Product getProduct() { return product; }
    int getQuantity() { return quantity; }
    void increaseQuantity(int amount) {
        if (amount <= 0) throw new IllegalArgumentException("数量は1以上です");
        quantity = Math.addExact(quantity, amount);
    }
    int subtotal() { return Math.multiplyExact(product.getUnitPriceYen(), quantity); }
}
