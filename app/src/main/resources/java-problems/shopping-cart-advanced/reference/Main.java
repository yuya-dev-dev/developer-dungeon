package jp.yuya.dev.developerdungeon.javaproblems.shopping.cart.advanced;

public final class Main {
    public static void main(String[] args) {
        Inventory inventory = new Inventory();
        inventory.stock("P-BOOK", 4); inventory.stock("P-MUG", 1);
        CheckoutService checkout = new CheckoutService(inventory);

        ShoppingCart first = new ShoppingCart();
        first.add(new CartItem("P-BOOK", "Java設計入門", new Money(1200), 2));
        first.add(new CartItem("P-MUG", "マグカップ", new Money(800), 1));
        Order firstOrder = checkout.checkout(first, new RateDiscount(10));
        check(firstOrder.total().yen() == 2880 && firstOrder.status() == OrderStatus.PENDING, "割合割引");
        expectFailure(firstOrder::ship);
        check(firstOrder.status() == OrderStatus.PENDING, "不正発送後");
        firstOrder.pay(); firstOrder.ship();
        check(firstOrder.status() == OrderStatus.SHIPPED, "発送完了");

        ShoppingCart failure = new ShoppingCart();
        failure.add(new CartItem("P-BOOK", "Java設計入門", new Money(1200), 1));
        failure.add(new CartItem("P-MUG", "マグカップ", new Money(800), 1));
        expectFailure(() -> checkout.checkout(failure, new NoDiscount()));

        ShoppingCart cancelCart = new ShoppingCart();
        cancelCart.add(new CartItem("P-BOOK", "Java設計入門", new Money(1200), 2));
        Order canceled = checkout.checkout(cancelCart, new FixedDiscount(new Money(100)));
        check(canceled.total().yen() == 2300, "固定割引");
        canceled.cancel();
        check(canceled.status() == OrderStatus.CANCELED, "取消");

        ShoppingCart proof = new ShoppingCart();
        proof.add(new CartItem("P-BOOK", "Java設計入門", new Money(1200), 2));
        check(checkout.checkout(proof, new NoDiscount()).total().yen() == 2400, "在庫復元と部分予約なし");
        System.out.println("ショッピングカート・上級: 動作確認完了");
    }

    private static void check(boolean condition, String label) { if (!condition) throw new AssertionError(label); }
    private static void expectFailure(Runnable action) {
        try { action.run(); throw new AssertionError("例外が必要です"); }
        catch (IllegalStateException expected) { }
    }
}
