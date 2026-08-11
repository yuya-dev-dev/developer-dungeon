package jp.yuya.dev.developerdungeon.javaproblems.shopping.cart.intermediate;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class CheckoutService {
    private final Inventory inventory;
    private final Clock clock;
    public CheckoutService(Inventory inventory, Clock clock) { this.inventory = inventory; this.clock = clock; }

    public Order checkout(ShoppingCart cart) {
        List<CartItem> items = cart.items();
        if (items.isEmpty()) throw new IllegalStateException("カートが空です");
        if (!inventory.hasAll(items)) throw new IllegalStateException("在庫が不足しています");
        List<OrderItem> snapshots = items.stream().map(item -> new OrderItem(item.product().id(), item.product().name(),
                item.product().unitPriceYen(), item.quantity())).toList();
        inventory.takeAll(items);
        Order order = new Order(LocalDateTime.now(clock), snapshots);
        cart.clear();
        return order;
    }
}

record Product(String id, String name, int unitPriceYen) { Product { Objects.requireNonNull(id); Objects.requireNonNull(name); if (unitPriceYen <= 0) throw new IllegalArgumentException(); } }
record CartItem(Product product, int quantity) { CartItem { if (quantity <= 0) throw new IllegalArgumentException(); } }
record OrderItem(String productId, String productName, int unitPriceYen, int quantity) { int subtotal() { return Math.multiplyExact(unitPriceYen, quantity); } }
record Order(LocalDateTime orderedAt, List<OrderItem> items) {
    Order { items = List.copyOf(items); }
    int totalYen() { return items.stream().mapToInt(OrderItem::subtotal).sum(); }
}
final class ShoppingCart {
    private final List<CartItem> items = new ArrayList<>();
    void add(Product product, int quantity) {
        Objects.requireNonNull(product);
        if (quantity <= 0) throw new IllegalArgumentException();
        for (int i = 0; i < items.size(); i++) {
            CartItem existing = items.get(i);
            if (existing.product().id().equals(product.id())) {
                if (!existing.product().equals(product)) throw new IllegalArgumentException("商品情報が一致しません");
                items.set(i, new CartItem(product, Math.addExact(existing.quantity(), quantity)));
                return;
            }
        }
        items.add(new CartItem(product, quantity));
    }
    void changeQuantity(String productId, int quantity) {
        if (quantity <= 0) throw new IllegalArgumentException();
        for (int i = 0; i < items.size(); i++) if (items.get(i).product().id().equals(productId)) {
            items.set(i, new CartItem(items.get(i).product(), quantity)); return;
        }
        throw new IllegalArgumentException("商品がありません");
    }
    void remove(String productId) { items.removeIf(item -> item.product().id().equals(productId)); }
    List<CartItem> items() { return List.copyOf(items); }
    void clear() { items.clear(); }
}
final class Inventory {
    private final Map<String, Integer> quantities = new HashMap<>();
    void stock(String productId, int quantity) { quantities.put(productId, quantity); }
    boolean hasAll(List<CartItem> items) {
        return requestedQuantities(items).entrySet().stream()
                .allMatch(entry -> quantities.getOrDefault(entry.getKey(), 0) >= entry.getValue());
    }
    void takeAll(List<CartItem> items) {
        Map<String, Integer> requested = requestedQuantities(items);
        if (requested.entrySet().stream()
                .anyMatch(entry -> quantities.getOrDefault(entry.getKey(), 0) < entry.getValue())) {
            throw new IllegalStateException("在庫不足");
        }
        requested.forEach((productId, quantity) ->
                quantities.compute(productId, (id, current) -> current - quantity));
    }
    private static Map<String, Integer> requestedQuantities(List<CartItem> items) {
        Map<String, Integer> requested = new HashMap<>();
        items.forEach(item -> requested.merge(item.product().id(), item.quantity(), Math::addExact));
        return requested;
    }
}
