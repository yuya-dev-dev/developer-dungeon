package jp.yuya.dev.developerdungeon.javaproblems.vending.machine.beginner;

import java.util.Objects;

public final class VendingMachine {
    private final Product product;
    private int stock;
    private int insertedYen;

    public VendingMachine(Product product, int stock) {
        this.product = Objects.requireNonNull(product);
        if (stock < 0) throw new IllegalArgumentException("在庫は0以上です");
        this.stock = stock;
    }

    public int getStock() { return stock; }
    public int getInsertedYen() { return insertedYen; }
    public void insert(int yen) {
        if (yen <= 0) throw new IllegalArgumentException("投入額は1円以上です");
        insertedYen = Math.addExact(insertedYen, yen);
    }
    public boolean canPurchase() { return stock > 0 && insertedYen >= product.getPriceYen(); }
    public Product purchase() {
        if (!canPurchase()) throw new IllegalStateException("在庫または投入金額が不足しています");
        stock--;
        insertedYen -= product.getPriceYen();
        return product;
    }
    public int refund() {
        int refund = insertedYen;
        insertedYen = 0;
        return refund;
    }
}

final class Product {
    private final String name;
    private final int priceYen;
    Product(String name, int priceYen) {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("商品名が必要です");
        if (priceYen <= 0) throw new IllegalArgumentException("価格は1円以上です");
        this.name = name;
        this.priceYen = priceYen;
    }
    String getName() { return name; }
    int getPriceYen() { return priceYen; }
}
