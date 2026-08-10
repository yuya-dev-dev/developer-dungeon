package jp.yuya.dev.developerdungeon.javaproblems.library.advanced;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class LibraryService {
    private final Map<String, Member> members = new HashMap<>();
    private final Map<String, BookCopy> copies = new HashMap<>();
    private final Map<String, ArrayDeque<String>> reservations = new HashMap<>();
    private final List<Loan> loans = new ArrayList<>();
    private final Clock clock;
    public LibraryService(Clock clock) { this.clock = Objects.requireNonNull(clock); }
    public void register(Member member) { members.put(member.id(), member); }
    public void addCopy(BookCopy copy) { copies.put(copy.id(), copy); }

    public Loan lend(String memberId, String copyId) {
        Member member = requiredMember(memberId);
        BookCopy copy = requiredCopy(copyId);
        List<Loan> open = openLoans(memberId);
        if (open.size() >= member.policy().maxLoans()) throw new IllegalStateException("貸出上限です");
        if (open.stream().anyMatch(loan -> loan.isbn().equals(copy.isbn()))) throw new IllegalStateException("同じISBNを借入中です");
        copy.assertLendableTo(memberId);
        LocalDate today = LocalDate.now(clock);
        Loan loan = new Loan(memberId, copy.id(), copy.isbn(), today, today.plusDays(member.policy().loanDays()));
        copy.lendTo(memberId);
        ArrayDeque<String> queue = reservations.get(copy.isbn());
        if (queue != null && memberId.equals(queue.peekFirst())) queue.removeFirst();
        loans.add(loan);
        return loan;
    }

    public void reserve(String memberId, String isbn) {
        requiredMember(memberId);
        if (openLoans(memberId).stream().anyMatch(loan -> loan.isbn().equals(isbn))) throw new IllegalStateException("借入中ISBNです");
        ArrayDeque<String> queue = reservations.computeIfAbsent(isbn, ignored -> new ArrayDeque<>());
        if (queue.contains(memberId)) throw new IllegalStateException("重複予約です");
        queue.addLast(memberId);
    }

    public void returnCopy(String memberId, String copyId) {
        BookCopy copy = requiredCopy(copyId);
        Loan loan = openLoans(memberId).stream().filter(item -> item.copyId().equals(copyId)).findFirst()
                .orElseThrow(() -> new IllegalStateException("貸出記録がありません"));
        ArrayDeque<String> queue = reservations.get(copy.isbn());
        String nextMember = queue == null ? null : queue.peekFirst();
        loan.returnOn(LocalDate.now(clock));
        copy.returnAndReserveFor(nextMember);
    }

    public void extend(String memberId, String copyId) {
        Loan loan = openLoans(memberId).stream().filter(item -> item.copyId().equals(copyId)).findFirst()
                .orElseThrow(() -> new IllegalStateException("貸出記録がありません"));
        ArrayDeque<String> queue = reservations.get(loan.isbn());
        loan.extend(LocalDate.now(clock), queue != null && !queue.isEmpty(), 7);
    }

    public List<Loan> openLoans(String memberId) { return loans.stream().filter(loan -> loan.memberId().equals(memberId) && loan.open()).toList(); }
    public long availableCopies(String isbn) { return copies.values().stream().filter(copy -> copy.isbn().equals(isbn) && copy.status() == CopyStatus.AVAILABLE).count(); }
    private Member requiredMember(String id) { Member value = members.get(id); if (value == null) throw new IllegalArgumentException("未知の利用者"); return value; }
    private BookCopy requiredCopy(String id) { BookCopy value = copies.get(id); if (value == null) throw new IllegalArgumentException("未知のcopy"); return value; }
}

interface LoanPolicy { int maxLoans(); int loanDays(); }
record StandardPolicy() implements LoanPolicy { public int maxLoans() { return 3; } public int loanDays() { return 14; } }
record PriorityPolicy() implements LoanPolicy { public int maxLoans() { return 5; } public int loanDays() { return 21; } }
record Member(String id, LoanPolicy policy) { Member { Objects.requireNonNull(id); Objects.requireNonNull(policy); } }
enum CopyStatus { AVAILABLE, LOANED, RESERVED }
final class BookCopy {
    private final String id;
    private final String isbn;
    private CopyStatus status = CopyStatus.AVAILABLE;
    private String assignedMemberId;
    BookCopy(String id, String isbn) { this.id = id; this.isbn = isbn; }
    String id() { return id; } String isbn() { return isbn; } CopyStatus status() { return status; }
    void assertLendableTo(String memberId) {
        if (status == CopyStatus.LOANED || status == CopyStatus.RESERVED && !memberId.equals(assignedMemberId)) throw new IllegalStateException("貸出できません");
    }
    void lendTo(String memberId) { assertLendableTo(memberId); status = CopyStatus.LOANED; assignedMemberId = memberId; }
    void returnAndReserveFor(String memberId) { status = memberId == null ? CopyStatus.AVAILABLE : CopyStatus.RESERVED; assignedMemberId = memberId; }
}
final class Loan {
    private final String memberId, copyId, isbn;
    private final LocalDate loanedOn;
    private LocalDate dueOn, returnedOn;
    private boolean extended;
    Loan(String memberId, String copyId, String isbn, LocalDate loanedOn, LocalDate dueOn) { this.memberId=memberId; this.copyId=copyId; this.isbn=isbn; this.loanedOn=loanedOn; this.dueOn=dueOn; }
    String memberId(){return memberId;} String copyId(){return copyId;} String isbn(){return isbn;} boolean open(){return returnedOn==null;}
    void returnOn(LocalDate date){if(!open())throw new IllegalStateException();returnedOn=date;}
    void extend(LocalDate today, boolean waiting, int days){if(!open()||extended||today.isAfter(dueOn)||waiting)throw new IllegalStateException("延長不可");dueOn=dueOn.plusDays(days);extended=true;}
}
