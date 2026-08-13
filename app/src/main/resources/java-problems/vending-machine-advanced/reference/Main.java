package jp.yuya.dev.developerdungeon.javaproblems.vending.machine.advanced;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

public final class Main {
    public static void main(String[] args) {
        Clock clock = Clock.fixed(Instant.parse("2026-04-01T10:00:00Z"), ZoneOffset.UTC);
        VendingMachine machine = new VendingMachine(clock);
        Product coffee = new Product("P-COFFEE", "コーヒー");
        machine.addSlot("A1", coffee, 150, 2);

        SaleOutcome cash = machine.sell("A1", new CashPayment(200));
        check(cash.completed() && cash.returnedYen() == 50, "現金購入");
        check(cash.record().paymentMethod().equals("CASH")
                && cash.record().completedAt().equals(LocalDateTime.of(2026, 4, 1, 10, 0)), "販売記録");
        SaleRecord snapshot = cash.record();

        SaleOutcome rejected = machine.sell("A1", new CashlessPayment(false));
        check(!rejected.completed() && rejected.returnedYen() == 0, "cashless拒否");
        SaleOutcome cashless = machine.sell("A1", new CashlessPayment(true));
        check(cashless.completed() && cashless.returnedYen() == 0, "cashless成功");
        SaleOutcome soldOut = machine.sell("A1", new CashPayment(500));
        check(!soldOut.completed() && soldOut.returnedYen() == 500, "売切れ返金");
        check(snapshot.productName().equals("コーヒー") && snapshot.priceYen() == 150, "snapshot維持");
        System.out.println("自動販売機・上級: 動作確認完了");
    }

    private static void check(boolean condition, String label) { if (!condition) throw new AssertionError(label); }
}
