package jp.yuya.dev.developerdungeon.javaproblems.shopping.cart.advanced;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class CheckoutService {
    private final Inventory inventory;
    public CheckoutService(Inventory inventory) { this.inventory = inventory; }
    public Order checkout(ShoppingCart cart, List<DiscountPolicy> policies) {
        if (cart.items().isEmpty()) throw new IllegalStateException("カートが空です");
        Reservation reservation = inventory.reserveAll(cart.items());
        List<OrderItem> items = cart.items().stream().map(item -> new OrderItem(item.productId(), item.name(), item.unitPrice(), item.quantity())).toList();
        Money subtotal = items.stream().map(OrderItem::subtotal).reduce(Money.zero(), Money::plus);
        Money current = subtotal;
        List<String> applied = new ArrayList<>();
        for (DiscountPolicy policy : List.copyOf(policies)) { current = policy.apply(current); applied.add(policy.name()); }
        return new Order(items, applied, current, reservation);
    }
}

record Money(int yen) {
    Money { if (yen < 0) throw new IllegalArgumentException("負の金額です"); }
    static Money zero(){return new Money(0);}
    Money plus(Money other){return new Money(Math.addExact(yen,other.yen));}
    Money times(int quantity){return new Money(Math.multiplyExact(yen,quantity));}
    Money minusFloorZero(Money other){return new Money(Math.max(0,yen-other.yen));}
    Money percentageOff(int percent){return new Money(yen - yen*percent/100);}
}
interface DiscountPolicy { Money apply(Money subtotal); String name(); }
record NoDiscount() implements DiscountPolicy { public Money apply(Money subtotal){return subtotal;} public String name(){return "NONE";} }
record FixedDiscount(Money amount) implements DiscountPolicy { public Money apply(Money subtotal){return subtotal.minusFloorZero(amount);} public String name(){return "FIXED";} }
record RateDiscount(int percent) implements DiscountPolicy {
    RateDiscount { if(percent<0||percent>100)throw new IllegalArgumentException(); }
    public Money apply(Money subtotal){return subtotal.percentageOff(percent);} public String name(){return "RATE";}
}
record CartItem(String productId,String name,Money unitPrice,int quantity){CartItem{if(quantity<=0)throw new IllegalArgumentException();}}
record OrderItem(String productId,String productName,Money unitPrice,int quantity){Money subtotal(){return unitPrice.times(quantity);}}
final class ShoppingCart {
    private final List<CartItem> items=new ArrayList<>();
    void add(CartItem item){items.add(Objects.requireNonNull(item));}
    List<CartItem> items(){return List.copyOf(items);}
}
interface Reservation { void commit(); void release(); }
final class Inventory {
    private final Map<String,Integer> available=new HashMap<>();
    void stock(String id,int quantity){available.put(id,quantity);}
    Reservation reserveAll(List<CartItem> items){
        Map<String,Integer> requested=new HashMap<>();
        items.forEach(item->requested.merge(item.productId(),item.quantity(),Math::addExact));
        if(requested.entrySet().stream().anyMatch(entry->available.getOrDefault(entry.getKey(),0)<entry.getValue()))throw new IllegalStateException("在庫不足");
        requested.forEach((productId,quantity)->available.compute(productId,(id,count)->count-quantity));
        return new Reservation(){private boolean open=true;public void commit(){ensure();open=false;}public void release(){ensure();requested.forEach((productId,quantity)->available.merge(productId,quantity,Integer::sum));open=false;}private void ensure(){if(!open)throw new IllegalStateException();}};
    }
}
enum OrderStatus { PENDING, PAID, SHIPPED, CANCELED }
final class Order {
    private final List<OrderItem> items; private final List<String> discounts; private final Money total; private final Reservation reservation;
    private OrderStatus status=OrderStatus.PENDING;
    Order(List<OrderItem> items,List<String> discounts,Money total,Reservation reservation){this.items=List.copyOf(items);this.discounts=List.copyOf(discounts);this.total=total;this.reservation=reservation;}
    void pay(){if(status!=OrderStatus.PENDING)throw new IllegalStateException();reservation.commit();status=OrderStatus.PAID;}
    void cancel(){if(status!=OrderStatus.PENDING)throw new IllegalStateException();reservation.release();status=OrderStatus.CANCELED;}
    void ship(){if(status!=OrderStatus.PAID)throw new IllegalStateException();status=OrderStatus.SHIPPED;}
    OrderStatus status(){return status;} Money total(){return total;} List<OrderItem> items(){return items;} List<String> discounts(){return discounts;}
}
