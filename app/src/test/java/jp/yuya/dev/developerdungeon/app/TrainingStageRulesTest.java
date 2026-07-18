package jp.yuya.dev.developerdungeon.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import jp.yuya.dev.developerdungeon.contract.CommandKind;
import jp.yuya.dev.developerdungeon.contract.RepositorySnapshot;
import org.junit.jupiter.api.Test;

class TrainingStageRulesTest {
    private static final String BASE = "a".repeat(40);
    private static final String RESULT = "b".repeat(40);
    private static final String INTRO = "c".repeat(40);
    private static final String IGNORE = "d".repeat(40);
    private static final String CONFIG = "e".repeat(40);
    private static final String REPORT = "f".repeat(40);
    private static final String HANDOFF = "1".repeat(40);
    private final StageRules rules = new StageRules();

    @Test void trainingOneUsesFixedSyntaxAndGradesTheFinalStateInsteadOfCommandCount() {
        var definition = rules.definition("TRAINING-GIT-01");
        assertThat(rules.parse(definition, "git add onboarding/intro.txt").kind()).isEqualTo(CommandKind.ADD_TRAINING_INTRO);
        assertThat(rules.parse(definition, "git commit -m complete-training-01").kind()).isEqualTo(CommandKind.COMMIT_TRAINING_ONE);
        assertThatThrownBy(() -> rules.parse(definition, "git add .")).isInstanceOf(StageInputException.class);

        var initial = training(BASE, null, List.of("onboarding/intro.txt"), List.of("onboarding/intro.txt"),
                List.of(), List.of(), INTRO, "", "", "", "", false);
        var targets = rules.capture(definition, snapshot(BASE, List.of(), false, "main", initial));
        var finished = training(RESULT, null, List.of("onboarding/intro.txt"), List.of(), List.of(), List.of(),
                INTRO, "", "", "", "", false);

        StageGrade grade = rules.grade(definition, snapshot(RESULT, List.of(BASE), true, "main", finished), targets, 4, 3);
        assertThat(grade.cleared()).isTrue();
        assertThat(grade.stars()).isEqualTo(1);
    }

    @Test void trainingTwoRequiresTheGeneratedReportToBeIgnoredButNotCommitted() {
        var definition = rules.definition("TRAINING-GIT-02");
        assertThat(rules.parse(definition, "git restore --staged build/training-report.txt").kind())
                .isEqualTo(CommandKind.UNSTAGE_TRAINING_REPORT);
        assertThat(rules.parse(definition, "git add .gitignore").kind()).isEqualTo(CommandKind.ADD_TRAINING_IGNORE);
        assertThat(rules.parse(definition, "git add config/application-training.properties").kind())
                .isEqualTo(CommandKind.ADD_TRAINING_CONFIG);

        var initial = training(BASE, null, List.of(".gitignore", "config/application-training.properties"),
                List.of(".gitignore", "config/application-training.properties"), List.of("build/training-report.txt"),
                List.of(), "", IGNORE, CONFIG, REPORT, "", true);
        var targets = rules.capture(definition, snapshot(BASE, List.of(), false, "main", initial));
        var finished = training(RESULT, null, List.of(".gitignore", "config/application-training.properties"),
                List.of(), List.of(), List.of("build/training-report.txt"), "", IGNORE, CONFIG, REPORT, "", true);
        assertThat(rules.grade(definition, snapshot(RESULT, List.of(BASE), true, "main", finished), targets, 0, 0).cleared()).isTrue();

        var notIgnored = training(RESULT, null, List.of(".gitignore", "config/application-training.properties"),
                List.of(), List.of(), List.of(), "", IGNORE, CONFIG, REPORT, "", true);
        assertThat(rules.grade(definition, snapshot(RESULT, List.of(BASE), true, "main", notIgnored), targets, 0, 0).cleared()).isFalse();
    }

    @Test void trainingThreeKeepsMainFixedAndCommitsOnTheDedicatedBranch() {
        var definition = rules.definition("TRAINING-GIT-03");
        assertThat(rules.parse(definition, "git switch -c feature/onboarding").kind())
                .isEqualTo(CommandKind.SWITCH_CREATE_TRAINING_BRANCH);
        assertThat(rules.parse(definition, "git branch").kind()).isEqualTo(CommandKind.BRANCH);

        var initial = training(BASE, null, List.of("docs/handoff.md"), List.of("docs/handoff.md"),
                List.of(), List.of(), "", "", "", "", HANDOFF, false);
        var targets = rules.capture(definition, snapshot(BASE, List.of(), false, "main", initial));
        var finished = training(BASE, RESULT, List.of("docs/handoff.md"), List.of(), List.of(), List.of(),
                "", "", "", "", HANDOFF, false);
        StageGrade grade = rules.grade(definition,
                snapshot(RESULT, List.of(BASE), true, "feature/onboarding", finished), targets, 2, 1);
        assertThat(grade.cleared()).isTrue();
        assertThat(grade.stars()).isEqualTo(1);
    }

    private static RepositorySnapshot snapshot(String head, List<String> parents, boolean clean, String branch,
                                                 RepositorySnapshot.TrainingState training) {
        return new RepositorySnapshot(head, "2".repeat(40), "3".repeat(40), parents, clean, false, List.of(head),
                branch, "", "", false, false, false, RepositorySnapshot.StageThreeState.empty(),
                RepositorySnapshot.StageFourState.empty(), RepositorySnapshot.StageFiveState.empty(), training);
    }

    private static RepositorySnapshot.TrainingState training(String main, String trainingBranch, List<String> headPaths,
                                                               List<String> working, List<String> index, List<String> ignored,
                                                               String intro, String ignore, String config, String report,
                                                               String handoff, boolean reportExists) {
        return new RepositorySnapshot.TrainingState(main, trainingBranch, headPaths, working, index, List.of(), ignored,
                intro, ignore, config, report, handoff, reportExists);
    }
}
