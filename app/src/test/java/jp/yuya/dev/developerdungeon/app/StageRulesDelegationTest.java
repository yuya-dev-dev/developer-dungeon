package jp.yuya.dev.developerdungeon.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import jp.yuya.dev.developerdungeon.contract.CommandKind;
import jp.yuya.dev.developerdungeon.contract.GitCommand;
import jp.yuya.dev.developerdungeon.contract.RepositorySnapshot;
import org.junit.jupiter.api.Test;

class StageRulesDelegationTest {
    private static final String C1 = "a".repeat(40);
    private static final String C0 = "b".repeat(40);
    private static final String OTHER = "c".repeat(40);
    private static final String SAFE_TREE = "d".repeat(40);
    private final StageRules rules = new StageRules();

    @Test
    void keepsCatalogOrderAndKeys() {
        assertThat(rules.definitions()).extracting(StageDefinition::key)
                .containsExactly("STAGE-GIT-01", "STAGE-GIT-02", "STAGE-GIT-03", "STAGE-GIT-04", "STAGE-GIT-05");
        assertThat(rules.trainingDefinitions()).extracting(StageDefinition::key)
                .containsExactly("TRAINING-GIT-01", "TRAINING-GIT-02", "TRAINING-GIT-03");
    }

    @Test
    void authorizesOnlyIdsActuallyRevealedByStageSpecificHintFour() {
        StageRules.StageTargets targets = targets(C1, C0);
        for (String stageKey : List.of("STAGE-GIT-01", "STAGE-GIT-02", "STAGE-GIT-05")) {
            HashSet<String> displayed = new HashSet<>();
            rules.revealHintTargets(rules.definition(stageKey), 3, targets, displayed);
            assertThat(displayed).isEmpty();
            rules.revealHintTargets(rules.definition(stageKey), 4, targets, displayed);
            assertThat(displayed).contains(C1.substring(0, 12));
            if (stageKey.equals("STAGE-GIT-02")) assertThat(displayed).contains(C0.substring(0, 12));
            else assertThat(displayed).doesNotContain(C0.substring(0, 12));
        }
        for (String stageKey : List.of("STAGE-GIT-03", "STAGE-GIT-04", "TRAINING-GIT-01")) {
            HashSet<String> displayed = new HashSet<>();
            rules.revealHintTargets(rules.definition(stageKey), 4, targets, displayed);
            assertThat(displayed).isEmpty();
        }
    }

    @Test
    void recordsOnlyMatchingDisplayedObjectFormatsAndAllowedTargets() {
        StageRules.StageTargets targets = targets(C1, C0);
        HashSet<String> displayed = new HashSet<>();
        rules.recordDisplayedObjects(rules.definition("STAGE-GIT-01"), new GitCommand(CommandKind.LOG_ONELINE),
                C1.substring(0, 12) + " message\n" + OTHER.substring(0, 12) + " other", targets, displayed);
        assertThat(displayed).containsExactly(C1.substring(0, 12));

        displayed.clear();
        rules.recordDisplayedObjects(rules.definition("STAGE-GIT-01"), new GitCommand(CommandKind.LOG_ONELINE),
                C1.substring(0, 12) + "\twrong-format", targets, displayed);
        assertThat(displayed).isEmpty();

        rules.recordDisplayedObjects(rules.definition("STAGE-GIT-05"), new GitCommand(CommandKind.LOG_ONELINE_ALL_DECORATE),
                C1.substring(0, 12) + " hidden", targets, displayed);
        assertThat(displayed).isEmpty();
        rules.recordDisplayedObjects(rules.definition("STAGE-GIT-05"), new GitCommand(CommandKind.REFLOG_HEAD),
                C1.substring(0, 12) + "\treflog entry", targets, displayed);
        assertThat(displayed).containsExactly(C1.substring(0, 12));
    }

    @Test
    void rejectsUnseenAmbiguousAndStageSpecificWrongObjects() {
        StageDefinition stageOne = rules.definition("STAGE-GIT-01");
        assertThatThrownBy(() -> rules.normalize(stageOne, new GitCommand(CommandKind.SHOW, C1.substring(0, 12)),
                targets(C1, C0), Set.of()))
                .isInstanceOf(StageInputException.class);

        String ambiguous = C1.substring(0, 12) + "e".repeat(28);
        assertThatThrownBy(() -> rules.normalize(stageOne, new GitCommand(CommandKind.SHOW, C1.substring(0, 12)),
                new StageRules.StageTargets(C1, null, SAFE_TREE, Set.of(C1, ambiguous)), Set.of(C1.substring(0, 12))))
                .isInstanceOf(StageInputException.class);

        assertThatThrownBy(() -> rules.normalize(rules.definition("STAGE-GIT-02"),
                new GitCommand(CommandKind.CHERRY_PICK, C0.substring(0, 12)), targets(C1, C0),
                Set.of(C1.substring(0, 12), C0.substring(0, 12))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("通知機能のcommitだけを移してください。");
    }

    @Test
    void keepsClearNearMissAndStarBoundaries() {
        StageDefinition stage = rules.definition("STAGE-GIT-01");
        StageRules.StageTargets targets = new StageRules.StageTargets(C1, null, SAFE_TREE, Set.of(C1));
        RepositorySnapshot cleared = new RepositorySnapshot(OTHER, SAFE_TREE, "bad-tree", List.of(C1), true, false,
                List.of(OTHER, C1, C0));
        RepositorySnapshot wrongParent = new RepositorySnapshot(OTHER, SAFE_TREE, "bad-tree", List.of(C0), true, false,
                List.of(OTHER, C1, C0));

        assertThat(rules.grade(stage, cleared, targets, 0, 0).stars()).isEqualTo(3);
        assertThat(rules.grade(stage, cleared, targets, 0, 1).stars()).isEqualTo(2);
        assertThat(rules.grade(stage, cleared, targets, 3, 0).stars()).isEqualTo(1);
        assertThat(rules.grade(stage, wrongParent, targets, 0, 0).cleared()).isFalse();
    }

    private static StageRules.StageTargets targets(String primary, String secondary) {
        return new StageRules.StageTargets(primary, secondary, SAFE_TREE, Set.of(primary, secondary));
    }
}
