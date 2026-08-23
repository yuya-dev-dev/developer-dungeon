package jp.yuya.dev.developerdungeon.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class StageGuidanceTest {
    private static final String C1 = "a".repeat(40);
    private static final String C0 = "b".repeat(40);
    private final StageRules rules = new StageRules();
    private final StageRules.StageTargets targets = new StageRules.StageTargets(C1, C0, "tree", Set.of(C1, C0));

    @Test void allPlayableStagesUseConceptOnlyConstantGuidance() {
        assertThat(rules.definitions()).allSatisfy(stage -> {
            assertThat(stage.presentation().guidanceMode()).isEqualTo(StagePresentationPolicy.GuidanceMode.CONCEPT_ONLY);
            assertThat(stage.presentation().conceptCategories()).isNotEmpty();
        });
    }

    @Test void exactSyntaxAndConcreteTargetsAppearOnlyAtTheirHintLevels() {
        for (StageDefinition stage : rules.definitions()) {
            assertThat(rules.hints(stage, 1, targets).getFirst()).doesNotContain("git ");
            assertThat(rules.hints(stage, 2, targets).getFirst()).doesNotContain("git ");
            assertThat(rules.hints(stage, 3, targets).getFirst()).contains("git ");
        }

        assertThat(rules.hints(rules.definition("STAGE-GIT-01"), 3, targets).getFirst()).contains("<commit-id>");
        assertThat(rules.hints(rules.definition("STAGE-GIT-02"), 3, targets).getFirst()).contains("<branch>", "<commit-id>");
        assertThat(rules.hints(rules.definition("STAGE-GIT-03"), 3, targets).getFirst())
                .contains("git switch feature/search", "git stash pop", "git stash apply", "git stash drop");
        assertThat(rules.hints(rules.definition("STAGE-GIT-04"), 3, targets).getFirst()).contains("<branch>", "<file>");
        assertThat(rules.hints(rules.definition("STAGE-GIT-05"), 3, targets).getFirst()).contains("<branch>", "<commit-id>");

        assertThat(rules.hints(rules.definition("STAGE-GIT-01"), 4, targets).getFirst()).contains(C1.substring(0, 12));
        assertThat(rules.hints(rules.definition("STAGE-GIT-02"), 4, targets).getFirst()).contains(C1.substring(0, 12), C0.substring(0, 12));
        assertThat(rules.hints(rules.definition("STAGE-GIT-03"), 4, targets).getFirst()).contains("feature/search");
        assertThat(rules.hints(rules.definition("STAGE-GIT-04"), 4, targets).getFirst()).contains("messages.properties", "profile.description=");
        assertThat(rules.hints(rules.definition("STAGE-GIT-05"), 4, targets).getFirst())
                .contains("git branch feature/payment-retry " + C1.substring(0, 12), "git switch feature/payment-retry")
                .doesNotContain("<C1>");
    }

    @Test void hintFourAuthorizesOnlyTheStageSpecificObjectTargets() {
        HashSet<String> displayed = new HashSet<>();
        rules.revealHintTargets(rules.definition("STAGE-GIT-01"), 3, targets, displayed);
        assertThat(displayed).isEmpty();
        rules.revealHintTargets(rules.definition("STAGE-GIT-01"), 4, targets, displayed);
        assertThat(displayed).containsExactly(C1.substring(0, 12));
    }

    @Test void rejectedInputExplainsTheNextStepWithoutLeakingTheAllowlist() {
        StageDefinition stage = rules.definition("STAGE-GIT-01");

        assertThatThrownBy(() -> rules.parse(stage, "git stash push"))
                .isInstanceOf(StageInputException.class)
                .hasMessageContainingAll("構文または引数", "ヒント")
                .hasMessageNotContaining("git status /")
                .hasMessageNotContaining("git revert");
        assertThatThrownBy(() -> rules.parse(stage, "git status; git log"))
                .isInstanceOf(StageInputException.class)
                .hasMessageContainingAll("入力形式", "1回に1つ", "ヒント")
                .hasMessageNotContaining("git status /")
                .hasMessageNotContaining("git revert");
    }
}
