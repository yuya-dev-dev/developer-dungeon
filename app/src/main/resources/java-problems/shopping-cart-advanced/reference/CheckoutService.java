package jp.yuya.dev.developerdungeon.javaproblems.shopping.cart.advanced;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class CheckoutService {
    private final Inventory inventory;
    public CheckoutService(Inventory inventory) { this.inventory = inventory; }
    public Order checkout(ShoppingCart cart, DiscountPolicy policy) {
        if (cart.items().isEmpty()) throw new IllegalStateException("カートが空です");
        Objects.requireNonNull(policy);
        List<OrderItem> items = cart.items().stream().map(item -> new OrderItem(item.productId(), item.name(), item.unitPrice(), item.quantity())).toList();
        Money subtotal = items.stream().map(OrderItem::subtotal).reduce(Money.zero(), Money::plus);
        DiscountApplication applied = Objects.requireNonNull(policy.apply(subtotal));
        Objects.requireNonNull(applied.total());
        Reservation reservation = inventory.reserveAll(cart.items());
        return new Order(items, applied, applied.total(), reservation);
    }
}

record Money(int yen) {
    Money { if (yen < 0) throw new IllegalArgumentException("負の金額です"); }
    static Money zero(){return new Money(0);}
    Money plus(Money other){return new Money(Math.addExact(yen,other.yen));}
    Money times(int quantity){return new Money(Math.multiplyExact(yen,quantity));}
    Money minusFloorZero(Money other){return new Money(Math.max(0,yen-other.yen));}
    Money percentageOff(int percent){long discount=(long)yen*percent/100;return new Money(Math.toIntExact(yen-discount));}
}
record DiscountApplication(String policyName, String condition, Money total) {
    DiscountApplication { Objects.requireNonNull(policyName); Objects.requireNonNull(condition); Objects.requireNonNull(total); }
}
interface DiscountPolicy { DiscountApplication apply(Money subtotal); }
record NoDiscount() implements DiscountPolicy {
    public DiscountApplication apply(Money subtotal){return new DiscountApplication("NONE","割引なし",subtotal);}
}
record FixedDiscount(Money amount) implements DiscountPolicy {
    FixedDiscount { Objects.requireNonNull(amount); }
    public DiscountApplication apply(Money subtotal){return new DiscountApplication("FIXED",amount.yen()+"円",subtotal.minusFloorZero(amount));}
}
record RateDiscount(int percent) implements DiscountPolicy {
    RateDiscount { if(percent<0||percent>100)throw new IllegalArgumentException(); }
    public DiscountApplication apply(Money subtotal){return new DiscountApplication("RATE",percent+"%",subtotal.percentageOff(percent));}
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
    void stock(String id,int quantity){if(id==null||id.isBlank()||quantity<0)throw new IllegalArgumentException();available.put(id,quantity);}
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
    private final List<OrderItem> items; private final DiscountApplication discount; private final Money total; private final Reservation reservation;
    private OrderStatus status=OrderStatus.PENDING;
    Order(List<OrderItem> items,DiscountApplication discount,Money total,Reservation reservation){this.items=List.copyOf(items);this.discount=discount;this.total=total;this.reservation=reservation;}
    void pay(){if(status!=OrderStatus.PENDING)throw new IllegalStateException();reservation.commit();status=OrderStatus.PAID;}
    void cancel(){if(status!=OrderStatus.PENDING)throw new IllegalStateException();reservation.release();status=OrderStatus.CANCELED;}
    void ship(){if(status!=OrderStatus.PAID)throw new IllegalStateException();status=OrderStatus.SHIPPED;}
    OrderStatus status(){return status;} Money total(){return total;} List<OrderItem> items(){return items;} DiscountApplication discount(){return discount;}
}
