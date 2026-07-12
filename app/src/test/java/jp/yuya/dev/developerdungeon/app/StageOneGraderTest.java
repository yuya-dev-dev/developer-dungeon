package jp.yuya.dev.developerdungeon.app;

import static org.assertj.core.api.Assertions.*;
import java.util.List;
import jp.yuya.dev.developerdungeon.contract.RepositorySnapshot;
import org.junit.jupiter.api.Test;

class StageOneGraderTest {
    private final StageOneGrader grader = new StageOneGrader();
    @Test void awardsThreeStarsForARevertCommitWithSafeTree() {
        var snapshot = new RepositorySnapshot("r", "safe-tree", "bad-tree", List.of("c2"), true, false, List.of("r", "c2", "c1"));
        var grade = grader.grade(snapshot, "c2", "safe-tree", 0, 0);
        assertThat(grade.cleared()).isTrue(); assertThat(grade.stars()).isEqualTo(3);
    }
    @Test void rejectsHistoryRewriteEvenWhenTreeIsSafe() {
        var snapshot = new RepositorySnapshot("c1", "safe-tree", "", List.of(), true, false, List.of("c1"));
        assertThat(grader.grade(snapshot, "c2", "safe-tree", 0, 0).cleared()).isFalse();
    }
}
