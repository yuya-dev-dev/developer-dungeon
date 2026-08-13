package jp.yuya.dev.developerdungeon.javaproblems.vending.machine.beginner;

public final class Main {
    public static void main(String[] args) {
        Product water = new Product("水", 120);
        VendingMachine machine = new VendingMachine(water, 2);
        machine.insert(100);
        check(!machine.canPurchase(), "金額不足");
        expectFailure(machine::purchase);
        check(machine.getStock() == 2 && machine.getInsertedYen() == 100, "購入失敗後");

        machine.insert(50);
        check(machine.purchase() == water, "購入商品");
        check(machine.getStock() == 1 && machine.getInsertedYen() == 30, "購入成功後");
        check(machine.refund() == 30 && machine.getInsertedYen() == 0, "返金後");
        System.out.println("自動販売機・初級: 動作確認完了");
    }

    private static void check(boolean condition, String label) {
        if (!condition) throw new AssertionError(label);
    }
    private static void expectFailure(Runnable action) {
        try { action.run(); throw new AssertionError("例外が必要です"); }
        catch (IllegalStateException expected) { }
    }
}
