package jp.yuya.dev.developerdungeon.javaproblems.library.beginner;

import java.util.Objects;

public final class Book {
    private final String id;
    private final String title;
    private boolean borrowed;

    public Book(String id, String title) {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("本の識別子が必要です");
        if (title == null || title.isBlank()) throw new IllegalArgumentException("題名が必要です");
        this.id = id;
        this.title = title;
    }

    public String getId() { return id; }
    public String getTitle() { return title; }
    public boolean isBorrowed() { return borrowed; }

    public void borrowTo(LibraryMember member) {
        Objects.requireNonNull(member);
        if (borrowed) throw new IllegalStateException("貸出中の本です");
        if (member.hasBorrowedBook()) throw new IllegalStateException("利用者は既に本を借りています");
        member.borrow(this);
        borrowed = true;
    }

    public void returnFrom(LibraryMember member) {
        Objects.requireNonNull(member);
        if (!borrowed) throw new IllegalStateException("貸出されていません");
        Book returned = member.returnBook();
        if (returned != this) {
            member.borrow(returned);
            throw new IllegalStateException("貸出情報が一致しません");
        }
        borrowed = false;
    }
}

final class LibraryMember {
    private final String id;
    private final String name;
    private Book borrowedBook;

    LibraryMember(String id, String name) {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("利用者識別子が必要です");
        if (name == null || name.isBlank()) throw new IllegalArgumentException("氏名が必要です");
        this.id = id;
        this.name = name;
    }

    String getId() { return id; }
    String getName() { return name; }
    boolean hasBorrowedBook() { return borrowedBook != null; }
    void borrow(Book book) {
        if (hasBorrowedBook()) throw new IllegalStateException("既に本を借りています");
        borrowedBook = Objects.requireNonNull(book);
    }
    Book returnBook() {
        if (borrowedBook == null) throw new IllegalStateException("借りている本がありません");
        Book returned = borrowedBook;
        borrowedBook = null;
        return returned;
    }
}
