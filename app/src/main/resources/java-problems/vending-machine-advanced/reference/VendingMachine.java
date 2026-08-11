package jp.yuya.dev.developerdungeon.javaproblems.vending.machine.advanced;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public final class VendingMachine {
    private final Map<String, Slot> slots = new HashMap<>();
    private final Clock clock;
    public VendingMachine(Clock clock) { this.clock = Objects.requireNonNull(clock); }
    public void addSlot(String code, Product product, int priceYen, int stock) {
        if (code == null || code.isBlank() || priceYen <= 0 || stock < 0) throw new IllegalArgumentException();
        slots.put(code, new Slot(Objects.requireNonNull(product), priceYen, stock));
    }

    public SaleOutcome sell(String slotCode, PaymentMethod payment) {
        SaleTransaction transaction = new SaleTransaction();
        Slot slot = slots.get(slotCode);
        if (slot == null || slot.stock() == 0) return transaction.cancel(payment.refundYen(), "購入不可");
        PaymentApproval approval = payment.authorize(slot.priceYen());
        if (!approval.approved()) return transaction.cancel(approval.returnedYen(), approval.reason());
        transaction.approve();
        slot.takeOne();
        transaction.complete();
        SaleRecord record = new SaleRecord(slot.product().name(), slot.priceYen(), payment.name(), LocalDateTime.now(clock));
        return SaleOutcome.completed(record, approval.returnedYen());
    }
}

record Product(String id, String name) { Product { Objects.requireNonNull(id); Objects.requireNonNull(name); } }
record PaymentApproval(boolean approved, int returnedYen, String reason) { }
interface PaymentMethod { PaymentApproval authorize(int priceYen); int refundYen(); String name(); }
final class CashPayment implements PaymentMethod {
    private final int insertedYen;
    CashPayment(int insertedYen) { if (insertedYen < 0) throw new IllegalArgumentException(); this.insertedYen = insertedYen; }
    public PaymentApproval authorize(int priceYen) { return insertedYen >= priceYen ? new PaymentApproval(true, insertedYen-priceYen, null) : new PaymentApproval(false, insertedYen, "投入不足"); }
    public int refundYen() { return insertedYen; }
    public String name() { return "CASH"; }
}
final class CashlessPayment implements PaymentMethod {
    private final boolean externalApproved;
    CashlessPayment(boolean externalApproved) { this.externalApproved = externalApproved; }
    public PaymentApproval authorize(int priceYen) { return new PaymentApproval(externalApproved, 0, externalApproved ? null : "決済拒否"); }
    public int refundYen() { return 0; }
    public String name() { return "CASHLESS"; }
}
enum TransactionStatus { STARTED, APPROVED, COMPLETED, CANCELED }
final class SaleTransaction {
    private TransactionStatus status = TransactionStatus.STARTED;
    void approve(){if(status!=TransactionStatus.STARTED)throw new IllegalStateException();status=TransactionStatus.APPROVED;}
    void complete(){if(status!=TransactionStatus.APPROVED)throw new IllegalStateException();status=TransactionStatus.COMPLETED;}
    SaleOutcome cancel(int refund, String reason){if(status!=TransactionStatus.STARTED)throw new IllegalStateException();status=TransactionStatus.CANCELED;return SaleOutcome.canceled(refund,reason);}
}
record SaleRecord(String productName, int priceYen, String paymentMethod, LocalDateTime completedAt) { }
record SaleOutcome(boolean completed, SaleRecord record, int returnedYen, String failureReason) {
    static SaleOutcome completed(SaleRecord record,int returned){return new SaleOutcome(true,record,returned,null);}
    static SaleOutcome canceled(int refund,String reason){return new SaleOutcome(false,null,refund,reason);}
}
final class Slot {
    private final Product product; private int priceYen, stock;
    Slot(Product product,int priceYen,int stock){this.product=product;this.priceYen=priceYen;this.stock=stock;}
    Product product(){return product;} int priceYen(){return priceYen;} int stock(){return stock;}
    void takeOne(){if(stock<=0)throw new IllegalStateException();stock--;}
}
