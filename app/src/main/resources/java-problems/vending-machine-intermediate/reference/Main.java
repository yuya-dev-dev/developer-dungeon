package jp.yuya.dev.developerdungeon.javaproblems.vending.machine.intermediate;

public final class Main {
    public static void main(String[] args) {
        Product cola = new Product("P-COLA", "コーラ");
        Product tea = new Product("P-TEA", "お茶");
        VendingMachine machine = new VendingMachine();
        machine.addSlot("A1", cola, 150, 1);
        machine.addSlot("A2", cola, 130, 1);
        machine.addSlot("B1", tea, 120, 0);

        machine.insert(100);
        PurchaseResult shortfall = machine.purchase("A1");
        check(!shortfall.success() && shortfall.failure() == PurchaseFailure.INSUFFICIENT_BALANCE, "金額不足");
        check(machine.refund() == 100 && machine.salesYen() == 0, "不足時返金");

        machine.insert(200);
        PurchaseResult first = machine.purchase("A1");
        check(first.success() && first.product().equals(cola) && first.purchasedPriceYen() == 150 && first.returnedYen() == 50, "A1購入");
        check(machine.salesYen() == 150, "A1売上");
        machine.insert(200);
        PurchaseResult soldOut = machine.purchase("A1");
        check(!soldOut.success() && soldOut.failure() == PurchaseFailure.OUT_OF_STOCK, "売切れ");
        check(machine.refund() == 200 && machine.salesYen() == 150, "売切れ失敗後");

        machine.insert(200);
        PurchaseResult second = machine.purchase("A2");
        check(second.success() && second.purchasedPriceYen() == 130 && second.returnedYen() == 70, "A2購入");
        check(machine.salesYen() == 280, "累計売上");
        System.out.println("自動販売機・中級: 動作確認完了");
    }

    private static void check(boolean condition, String label) { if (!condition) throw new AssertionError(label); }
}
