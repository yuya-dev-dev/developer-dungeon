package jp.yuya.dev.developerdungeon.app;

import jp.yuya.dev.developerdungeon.contract.RepositorySnapshot;
import org.springframework.stereotype.Component;

@Component
class StageOneGrader {
    Grade grade(RepositorySnapshot snapshot, String badCommitId, String safeTreeId, int highestHint, int playerResets) {
        boolean cleared = snapshot.clean() && !snapshot.revertInProgress()
                && snapshot.ancestorObjectIds().contains(badCommitId)
                && safeTreeId.equals(snapshot.headTreeId())
                && snapshot.headParents().size() == 1
                && snapshot.headParents().getFirst().equals(badCommitId);
        if (!cleared) return new Grade(false, 0, "復旧条件はまだ満たしていません。");
        int stars = highestHint >= 3 ? 1 : playerResets > 0 ? 2 : 3;
        return new Grade(true, stars, "公開済みの誤commitを履歴に残したまま、安全に取り消せました。");
    }

    record Grade(boolean cleared, int stars, String message) { }
}
