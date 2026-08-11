package jp.yuya.dev.developerdungeon.javaproblems.vending.machine.intermediate;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public final class VendingMachine {
    private final Map<String, Slot> slots = new HashMap<>();
    private int balanceYen;
    private int salesYen;

    public void addSlot(String code, Product product, int priceYen, int stock) {
        if (code == null || code.isBlank() || priceYen <= 0 || stock < 0) throw new IllegalArgumentException();
        slots.put(code, new Slot(product, priceYen, stock));
    }
    public void insert(int yen) { if (yen <= 0) throw new IllegalArgumentException(); balanceYen = Math.addExact(balanceYen, yen); }
    public PurchaseResult purchase(String code) {
        Slot slot = slots.get(code);
        if (slot == null) return PurchaseResult.failure(PurchaseFailure.UNKNOWN_SLOT);
        if (slot.stock() == 0) return PurchaseResult.failure(PurchaseFailure.OUT_OF_STOCK);
        if (balanceYen < slot.priceYen()) return PurchaseResult.failure(PurchaseFailure.INSUFFICIENT_BALANCE);
        int returned = balanceYen - slot.priceYen();
        int updatedSalesYen = Math.addExact(salesYen, slot.priceYen());
        slot.takeOne();
        salesYen = updatedSalesYen;
        balanceYen = 0;
        return PurchaseResult.success(slot.product(), slot.priceYen(), returned);
    }
    public int refund() { int refund = balanceYen; balanceYen = 0; return refund; }
    public int salesYen() { return salesYen; }
}

record Product(String id, String name) { Product { Objects.requireNonNull(id); Objects.requireNonNull(name); } }
enum PurchaseFailure { UNKNOWN_SLOT, OUT_OF_STOCK, INSUFFICIENT_BALANCE }
record PurchaseResult(boolean success, Product product, int purchasedPriceYen, int returnedYen, PurchaseFailure failure) {
    static PurchaseResult success(Product product, int price, int returned) { return new PurchaseResult(true, product, price, returned, null); }
    static PurchaseResult failure(PurchaseFailure failure) { return new PurchaseResult(false, null, 0, 0, failure); }
}
final class Slot {
    private final Product product;
    private final int priceYen;
    private int stock;
    Slot(Product product, int priceYen, int stock) { this.product = Objects.requireNonNull(product); this.priceYen = priceYen; this.stock = stock; }
    Product product() { return product; }
    int priceYen() { return priceYen; }
    int stock() { return stock; }
    void takeOne() { if (stock == 0) throw new IllegalStateException(); stock--; }
}
