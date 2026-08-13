package jp.yuya.dev.developerdungeon.javaproblems.library.advanced;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

public final class Main {
    public static void main(String[] args) {
        Clock clock = Clock.fixed(Instant.parse("2026-04-01T00:00:00Z"), ZoneOffset.UTC);
        LibraryService library = new LibraryService(clock);
        BookTitle title = new BookTitle("ISBN-001", "ドメイン駆動設計");
        BookCopy copy1 = new BookCopy("C-001", title.isbn());
        BookCopy copy2 = new BookCopy("C-002", title.isbn());
        Member standard = new Member("M-STANDARD", new StandardPolicy());
        Member priority = new Member("M-PRIORITY", new PriorityPolicy());
        Member waiting = new Member("M-WAITING", new StandardPolicy());
        library.addTitle(title); library.addCopy(copy1); library.addCopy(copy2);
        library.register(standard); library.register(priority); library.register(waiting);

        Loan standardLoan = library.lend(standard.id(), copy1.id());
        check(standardLoan.dueOn().equals(LocalDate.of(2026, 4, 15)), "標準期限");
        library.reserve(priority.id(), title.isbn());
        library.reserve(waiting.id(), title.isbn());
        library.returnCopy(standard.id(), copy1.id());
        expectFailure(() -> library.lend(waiting.id(), copy1.id()));
        check(copy1.isReservedFor(priority.id()), "優先会員への引当維持");

        Loan priorityLoan = library.lend(priority.id(), copy1.id());
        check(priorityLoan.dueOn().equals(LocalDate.of(2026, 4, 22)), "優先期限");
        expectFailure(() -> library.extend(priority.id(), copy1.id()));
        check(!priorityLoan.extended() && priorityLoan.dueOn().equals(LocalDate.of(2026, 4, 22)), "延長失敗後");
        library.returnCopy(priority.id(), copy1.id());
        check(copy1.isReservedFor(waiting.id()), "次予約者への引当");
        library.lend(waiting.id(), copy1.id());

        Loan extendable = library.lend(standard.id(), copy2.id());
        library.extend(standard.id(), copy2.id());
        check(extendable.extended() && extendable.dueOn().equals(LocalDate.of(2026, 4, 22)), "延長成功");
        expectFailure(() -> library.extend(standard.id(), copy2.id()));
        check(extendable.dueOn().equals(LocalDate.of(2026, 4, 22)), "二重延長失敗後");
        System.out.println("図書館貸出・上級: 動作確認完了");
    }

    private static void check(boolean condition, String label) { if (!condition) throw new AssertionError(label); }
    private static void expectFailure(Runnable action) {
        try { action.run(); throw new AssertionError("例外が必要です"); }
        catch (IllegalStateException expected) { }
    }
}
