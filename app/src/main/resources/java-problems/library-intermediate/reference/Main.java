package jp.yuya.dev.developerdungeon.javaproblems.library.intermediate;

import java.time.LocalDate;

public final class Main {
    public static void main(String[] args) {
        Book title = new Book("ISBN-001", "Clean Code");
        BookCopy copy1 = new BookCopy("C-001", title);
        BookCopy copy2 = new BookCopy("C-002", title);
        Member aya = new Member("M-001", "綾");
        Member ken = new Member("M-002", "健");
        LibraryService library = new LibraryService();
        library.register(aya); library.register(ken);
        library.addCopy(copy1); library.addCopy(copy2);

        Loan ayaLoan = library.lend(aya.id(), copy1.copyId(), LocalDate.of(2026, 4, 1));
        library.lend(ken.id(), copy2.copyId(), LocalDate.of(2026, 4, 1));
        check(ayaLoan.dueOn().equals(LocalDate.of(2026, 4, 15)), "返却期限");
        check(library.availableCopies(title.isbn()) == 0, "貸出後冊数");
        expectFailure(() -> library.returnCopy(ken.id(), copy1.copyId(), LocalDate.of(2026, 4, 5)));
        check(library.openLoans(aya.id()).size() == 1 && library.openLoans(ken.id()).size() == 1, "誤返却失敗後");
        check(copy1.loaned() && copy2.loaned(), "copy状態");

        library.returnCopy(aya.id(), copy1.copyId(), LocalDate.of(2026, 4, 5));
        check(library.availableCopies(title.isbn()) == 1, "返却後冊数");
        check(library.openLoans(aya.id()).isEmpty() && library.loanHistory().size() == 2, "履歴");
        System.out.println("図書館貸出・中級: 動作確認完了");
    }

    private static void check(boolean condition, String label) { if (!condition) throw new AssertionError(label); }
    private static void expectFailure(Runnable action) {
        try { action.run(); throw new AssertionError("例外が必要です"); }
        catch (IllegalStateException expected) { }
    }
}
