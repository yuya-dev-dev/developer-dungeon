package jp.yuya.dev.developerdungeon.app;

import java.util.List;
import jp.yuya.dev.developerdungeon.contract.RepositorySnapshot;

public record StageView(String requestId, String output, Integer exitCode, RepositorySnapshot snapshot,
                        int hintLevel, int resetCount, int systemRecoveryCount, long commandSequence,
                        boolean cleared, int stars, String gradeMessage, List<String> hints) {
    public StageView {
        hints = List.copyOf(hints);
    }
}
