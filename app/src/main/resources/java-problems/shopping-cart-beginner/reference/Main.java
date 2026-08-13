package jp.yuya.dev.developerdungeon.javaproblems.shopping.cart.beginner;

import java.util.List;

public final class Main {
    public static void main(String[] args) {
        Product notebook = new Product("P-001", "ノート", 500);
        Product pen = new Product("P-002", "ペン", 120);
        Product conflict = new Product("P-001", "ノート", 600);
        ShoppingCart cart = new ShoppingCart();
        cart.add(notebook, 2);
        cart.add(pen, 3);
        cart.add(notebook, 1);
        check(cart.getItems().size() == 2 && cart.totalQuantity() == 6 && cart.totalPriceYen() == 1860, "集計");

        expectFailure(() -> cart.add(conflict, 1));
        check(cart.totalQuantity() == 6 && cart.totalPriceYen() == 1860, "不整合商品失敗後");
        List<CartItem> snapshot = cart.getItems();
        expectUnsupported(snapshot::clear);
        snapshot.get(0).increaseQuantity(10);
        check(cart.totalQuantity() == 6 && cart.totalPriceYen() == 1860, "要素変更後");
        cart.clear();
        check(cart.totalQuantity() == 0 && cart.totalPriceYen() == 0, "全削除後");
        System.out.println("ショッピングカート・初級: 動作確認完了");
    }

    private static void check(boolean condition, String label) {
        if (!condition) throw new AssertionError(label);
    }
    private static void expectFailure(Runnable action) {
        try { action.run(); throw new AssertionError("例外が必要です"); }
        catch (IllegalArgumentException expected) { }
    }
    private static void expectUnsupported(Runnable action) {
        try { action.run(); throw new AssertionError("変更拒否が必要です"); }
        catch (UnsupportedOperationException expected) { }
    }
}
