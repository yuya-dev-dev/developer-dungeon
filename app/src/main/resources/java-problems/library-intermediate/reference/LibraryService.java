package jp.yuya.dev.developerdungeon.javaproblems.library.intermediate;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class LibraryService {
    private final Map<String, Member> members = new HashMap<>();
    private final Map<String, BookCopy> copies = new HashMap<>();
    private final List<Loan> loans = new ArrayList<>();

    public void register(Member member) { members.put(member.id(), member); }
    public void addCopy(BookCopy copy) { copies.put(copy.copyId(), copy); }

    public Loan lend(String memberId, String copyId, LocalDate loanedOn) {
        Member member = requiredMember(memberId);
        BookCopy copy = requiredCopy(copyId);
        if (openLoans(memberId).size() >= 3) throw new IllegalStateException("貸出上限です");
        if (copy.loaned()) throw new IllegalStateException("貸出中の蔵書です");
        Loan loan = new Loan(member.id(), copy.copyId(), loanedOn, loanedOn.plusDays(14));
        copy.lend();
        loans.add(loan);
        return loan;
    }

    public void returnCopy(String memberId, String copyId, LocalDate returnedOn) {
        BookCopy copy = requiredCopy(copyId);
        Loan loan = loans.stream().filter(item -> item.copyId().equals(copyId) && item.open()).findFirst()
                .orElseThrow(() -> new IllegalStateException("openな貸出がありません"));
        if (!loan.memberId().equals(memberId)) throw new IllegalStateException("返却者が一致しません");
        copy.assertLoaned();
        loan.close(returnedOn);
        copy.returned();
    }

    public List<Loan> openLoans(String memberId) {
        return loans.stream().filter(loan -> loan.memberId().equals(memberId) && loan.open()).toList();
    }
    public long availableCopies(String isbn) {
        return copies.values().stream().filter(copy -> copy.book().isbn().equals(isbn) && !copy.loaned()).count();
    }
    public List<Loan> loanHistory() { return List.copyOf(loans); }
    private Member requiredMember(String id) { Member value = members.get(id); if (value == null) throw new IllegalArgumentException("未知の利用者"); return value; }
    private BookCopy requiredCopy(String id) { BookCopy value = copies.get(id); if (value == null) throw new IllegalArgumentException("未知の蔵書"); return value; }
}

record Book(String isbn, String title) { Book { Objects.requireNonNull(isbn); Objects.requireNonNull(title); } }
record Member(String id, String name) { Member { Objects.requireNonNull(id); Objects.requireNonNull(name); } }
final class BookCopy {
    private final String copyId;
    private final Book book;
    private boolean loaned;
    BookCopy(String copyId, Book book) { this.copyId = Objects.requireNonNull(copyId); this.book = Objects.requireNonNull(book); }
    String copyId() { return copyId; }
    Book book() { return book; }
    boolean loaned() { return loaned; }
    void assertLoaned() { if (!loaned) throw new IllegalStateException(); }
    void lend() { if (loaned) throw new IllegalStateException(); loaned = true; }
    void returned() { if (!loaned) throw new IllegalStateException(); loaned = false; }
}
final class Loan {
    private final String memberId;
    private final String copyId;
    private final LocalDate loanedOn;
    private final LocalDate dueOn;
    private LocalDate returnedOn;
    Loan(String memberId, String copyId, LocalDate loanedOn, LocalDate dueOn) {
        this.memberId = memberId; this.copyId = copyId; this.loanedOn = loanedOn; this.dueOn = dueOn;
    }
    String memberId() { return memberId; }
    String copyId() { return copyId; }
    LocalDate dueOn() { return dueOn; }
    boolean open() { return returnedOn == null; }
    void close(LocalDate date) { if (!open()) throw new IllegalStateException(); returnedOn = Objects.requireNonNull(date); }
}
