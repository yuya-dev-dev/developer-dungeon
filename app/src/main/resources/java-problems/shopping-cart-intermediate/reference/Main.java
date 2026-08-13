package jp.yuya.dev.developerdungeon.javaproblems.shopping.cart.intermediate;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

public final class Main {
    public static void main(String[] args) {
        Product book = new Product("P-BOOK", "Java設計入門", 1200);
        Product pen = new Product("P-PEN", "ペン", 100);
        Inventory inventory = new Inventory();
        inventory.stock(book.id(), 3); inventory.stock(pen.id(), 2);
        Clock clock = Clock.fixed(Instant.parse("2026-04-01T01:00:00Z"), ZoneId.of("Asia/Tokyo"));
        CheckoutService checkout = new CheckoutService(inventory, clock);

        ShoppingCart first = new ShoppingCart();
        first.add(book, 1); first.add(book, 1); first.add(pen, 1);
        check(first.items().size() == 2, "重複行統合");
        Order order = checkout.checkout(first);
        check(order.totalYen() == 2500 && order.items().size() == 2, "注文snapshot");
        check(order.orderedAt().equals(LocalDateTime.of(2026, 4, 1, 10, 0)), "注文日時");
        check(first.items().isEmpty(), "checkout後のカート");

        ShoppingCart failure = new ShoppingCart();
        failure.add(book, 1); failure.add(pen, 2);
        expectFailure(() -> checkout.checkout(failure));
        check(failure.items().size() == 2, "在庫不足後のカート");
        ShoppingCart proof = new ShoppingCart(); proof.add(book, 1);
        check(checkout.checkout(proof).totalYen() == 1200, "部分減算なし");
        System.out.println("ショッピングカート・中級: 動作確認完了");
    }

    private static void check(boolean condition, String label) { if (!condition) throw new AssertionError(label); }
    private static void expectFailure(Runnable action) {
        try { action.run(); throw new AssertionError("例外が必要です"); }
        catch (IllegalStateException expected) { }
    }
}
