package jp.yuya.dev.developerdungeon.javaproblems.library.beginner;

public final class Main {
    public static void main(String[] args) {
        Book book = new Book("B-001", "Java入門");
        LibraryMember aoki = new LibraryMember("M-001", "青木");
        LibraryMember sato = new LibraryMember("M-002", "佐藤");
        check(!book.isBorrowed() && !aoki.hasBorrowedBook(), "初期状態");

        book.borrowTo(aoki);
        check(book.isBorrowed() && aoki.hasBorrowedBook(), "貸出状態");
        expectFailure(() -> book.borrowTo(sato));
        check(book.isBorrowed() && aoki.hasBorrowedBook() && !sato.hasBorrowedBook(), "二重貸出失敗後");

        book.returnFrom(aoki);
        check(!book.isBorrowed() && !aoki.hasBorrowedBook(), "返却状態");
        System.out.println("図書館貸出・初級: 動作確認完了");
    }

    private static void check(boolean condition, String label) {
        if (!condition) throw new AssertionError(label);
    }
    private static void expectFailure(Runnable action) {
        try { action.run(); throw new AssertionError("例外が必要です"); }
        catch (IllegalStateException expected) { }
    }
}
