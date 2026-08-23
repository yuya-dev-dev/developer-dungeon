package jp.yuya.dev.developerdungeon.runner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import jp.yuya.dev.developerdungeon.contract.CommandKind;
import jp.yuya.dev.developerdungeon.contract.GitCommand;
import jp.yuya.dev.developerdungeon.contract.RepositorySnapshot;
import org.junit.jupiter.api.Test;

class RunnerStagePolicyTest {
    private static final String A = "a".repeat(40);
    private static final String B = "b".repeat(40);
    private static final String C = "c".repeat(40);
    private static final String STAGE_FOUR_BLOB = "a6306bacd230ac74aaf017cde7717bc3eb83684c";
    private static final String STAGE_FOUR_MAIN_TREE = "63ec3ef493c5b54618798e50fe8d2e58bc40a4a9";
    private static final String STAGE_FOUR_FEATURE_TREE = "e4d8a76dfb74d699e48a7437d60811202ba7face";
    private static final String STAGE_FIVE_C0 = "4b03c129e4d5b2bfe41fb2afd208b13dab7824a1";
    private static final String STAGE_FIVE_C1 = "39194dda957695ace62387ecdc5f77fcd5ee81ea";
    private static final String STAGE_FIVE_TREE = "cbc2826a3bd49e4947b67c020e61dd5e4ca7adb3";
    private final RunnerStagePolicy policy = new RunnerStagePolicy(STAGE_FOUR_BLOB, STAGE_FOUR_MAIN_TREE,
            STAGE_FOUR_FEATURE_TREE, STAGE_FIVE_C0, STAGE_FIVE_C1, STAGE_FIVE_TREE);

    @Test
    void fixesTheAllowedAndRejectedCommandMatrixForEveryStage() {
        Map<String, Set<CommandKind>> allowed = Map.of(
                "STAGE-GIT-01", EnumSet.of(CommandKind.STATUS, CommandKind.LOG_ONELINE, CommandKind.SHOW,
                        CommandKind.REVERT_NO_EDIT, CommandKind.REVERT_NO_COMMIT, CommandKind.COMMIT_RESTORE_SETTINGS),
                "STAGE-GIT-02", EnumSet.of(CommandKind.STATUS, CommandKind.LOG_ONELINE_ALL_DECORATE, CommandKind.BRANCH,
                        CommandKind.SHOW, CommandKind.SWITCH, CommandKind.CHERRY_PICK, CommandKind.RESET_HARD),
                "STAGE-GIT-03", EnumSet.of(CommandKind.STATUS, CommandKind.DIFF, CommandKind.DIFF_STAGED, CommandKind.BRANCH,
                        CommandKind.STASH_PUSH, CommandKind.STASH_LIST, CommandKind.STASH_POP, CommandKind.STASH_APPLY,
                        CommandKind.STASH_DROP, CommandKind.SWITCH),
                "STAGE-GIT-04", EnumSet.of(CommandKind.STATUS, CommandKind.LOG_GRAPH_ALL, CommandKind.DIFF, CommandKind.BRANCH,
                        CommandKind.MERGE_PROFILE_MESSAGE, CommandKind.ADD_PROFILE_MESSAGES, CommandKind.COMMIT_NO_EDIT,
                        CommandKind.COMMIT_ALL_NO_EDIT),
                "STAGE-GIT-05", EnumSet.of(CommandKind.STATUS, CommandKind.LOG_ONELINE_ALL_DECORATE, CommandKind.REFLOG_HEAD,
                        CommandKind.SHOW, CommandKind.CREATE_PAYMENT_RETRY_BRANCH, CommandKind.SWITCH_PAYMENT_RETRY,
                        CommandKind.SWITCH_CREATE_PAYMENT_RETRY),
                "TRAINING-GIT-01", EnumSet.of(CommandKind.STATUS, CommandKind.DIFF, CommandKind.DIFF_STAGED,
                        CommandKind.LOG_ONELINE, CommandKind.ADD_TRAINING_INTRO, CommandKind.COMMIT_TRAINING_ONE),
                "TRAINING-GIT-02", EnumSet.of(CommandKind.STATUS, CommandKind.DIFF, CommandKind.DIFF_STAGED,
                        CommandKind.LOG_ONELINE, CommandKind.UNSTAGE_TRAINING_REPORT, CommandKind.ADD_TRAINING_IGNORE,
                        CommandKind.ADD_TRAINING_CONFIG, CommandKind.COMMIT_TRAINING_TWO),
                "TRAINING-GIT-03", EnumSet.of(CommandKind.STATUS, CommandKind.DIFF, CommandKind.DIFF_STAGED,
                        CommandKind.LOG_ONELINE, CommandKind.BRANCH, CommandKind.SWITCH_CREATE_TRAINING_BRANCH,
                        CommandKind.SWITCH_TRAINING_BRANCH, CommandKind.ADD_TRAINING_HANDOFF, CommandKind.COMMIT_TRAINING_THREE));

        for (var entry : allowed.entrySet()) {
            RunnerStagePolicy.Targets targets = targets(entry.getKey());
            for (CommandKind kind : CommandKind.values()) {
                GitCommand command = command(entry.getKey(), kind, targets);
                if (entry.getValue().contains(kind)) {
                    assertThatCode(() -> policy.validateCommand(entry.getKey(), command, targets))
                            .as(entry.getKey() + " " + kind)
                            .doesNotThrowAnyException();
                } else {
                    assertThatThrownBy(() -> policy.validateCommand(entry.getKey(), command, targets))
                            .as(entry.getKey() + " " + kind)
                            .isInstanceOf(IllegalArgumentException.class)
                            .hasMessage(entry.getKey().startsWith("TRAINING-")
                                    ? "command is not allowed for this training"
                                    : "command is not allowed for this stage");
                }
            }
        }
    }

    @Test
    void rejectsWrongBranchAndObjectTargetsWithTheExistingMessages() {
        assertThatThrownBy(() -> policy.validateRevertTarget(new GitCommand(CommandKind.REVERT_NO_EDIT, C), targets("STAGE-GIT-01")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("only the stage's accidental commit can be reverted");
        assertTargetFailure("STAGE-GIT-02", new GitCommand(CommandKind.CHERRY_PICK, C),
                "only the stage's notification commit can be cherry-picked");
        assertTargetFailure("STAGE-GIT-02", new GitCommand(CommandKind.RESET_HARD, C),
                "only the stage's original branch tip can be reset");
        assertTargetFailure("STAGE-GIT-02", GitCommand.switchTo("feature/other"),
                "only the stage's branches can be selected");
        assertTargetFailure("STAGE-GIT-03", GitCommand.switchTo("feature/other"),
                "only the stage's search branch can be selected");
        assertTargetFailure("STAGE-GIT-05", new GitCommand(CommandKind.CREATE_PAYMENT_RETRY_BRANCH, C),
                "only the reflog recovery commit can be used");
    }

    @Test
    void failsClosedForUnknownStagesAndTraining() {
        assertThatThrownBy(() -> policy.validateCommand("STAGE-GIT-99", new GitCommand(CommandKind.STATUS), targets("STAGE-GIT-01")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("command is not allowed for this stage");
        assertThatThrownBy(() -> policy.validateTrainingCommand("TRAINING-GIT-99", CommandKind.STATUS))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("command is not allowed for this training");
        assertThatThrownBy(() -> policy.captureTargets("STAGE-GIT-99", base(A, true, "main")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("stage fixture is invalid");
    }

    @Test
    void capturesTheFixedTargetsForEveryValidInitialFixture() {
        var stageOne = policy.captureTargets("STAGE-GIT-01", new RepositorySnapshot(A, B, "", List.of(), true, false,
                List.of(A, B), "main", "", "", false));
        assertThat(stageOne.revertTarget()).isEqualTo(A);
        assertThat(stageOne.allowedObjects()).containsExactlyInAnyOrder(A, B);

        var stageTwo = policy.captureTargets("STAGE-GIT-02", stageTwo());
        assertThat(stageTwo.resetTarget()).isEqualTo(B);
        assertThat(stageTwo.cherryPickTarget()).isEqualTo(A);

        assertThatCode(() -> policy.captureTargets("STAGE-GIT-03", stageThree(false))).doesNotThrowAnyException();
        assertThatCode(() -> policy.captureTargets("STAGE-GIT-04", stageFour(STAGE_FOUR_MAIN_TREE))).doesNotThrowAnyException();
        assertThat(policy.captureTargets("STAGE-GIT-05", stageFive(null)).recoveryTarget()).isEqualTo(STAGE_FIVE_C1);
        assertThatCode(() -> policy.captureTargets("TRAINING-GIT-01", trainingOne(false))).doesNotThrowAnyException();
        assertThatCode(() -> policy.captureTargets("TRAINING-GIT-02", trainingTwo(false))).doesNotThrowAnyException();
        assertThatCode(() -> policy.captureTargets("TRAINING-GIT-03", trainingThree(false))).doesNotThrowAnyException();
    }

    @Test
    void rejectsRepresentativeNearMissFixtures() {
        assertInvalidStage("STAGE-GIT-02", base(A, true, "main"));
        assertInvalidStage("STAGE-GIT-03", stageThree(true));
        assertInvalidStage("STAGE-GIT-04", stageFour(C));
        assertInvalidStage("STAGE-GIT-05", stageFive(C));
        assertThatThrownBy(() -> policy.captureTargets("TRAINING-GIT-01", trainingOne(true)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("training fixture is invalid");
        assertThatThrownBy(() -> policy.captureTargets("TRAINING-GIT-02", trainingTwo(true)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("training fixture is invalid");
        assertThatThrownBy(() -> policy.captureTargets("TRAINING-GIT-03", trainingThree(true)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("training fixture is invalid");
    }

    private void assertTargetFailure(String stageKey, GitCommand command, String message) {
        assertThatThrownBy(() -> policy.validateCommand(stageKey, command, targets(stageKey)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(message);
    }

    private void assertInvalidStage(String stageKey, RepositorySnapshot snapshot) {
        assertThatThrownBy(() -> policy.captureTargets(stageKey, snapshot))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("stage fixture is invalid");
    }

    private RunnerStagePolicy.Targets targets(String stageKey) {
        return switch (stageKey) {
            case "STAGE-GIT-01" -> new RunnerStagePolicy.Targets(A, null, null, null, Set.of(A, B));
            case "STAGE-GIT-02" -> new RunnerStagePolicy.Targets(null, B, A, null, Set.of(A, B));
            case "STAGE-GIT-05" -> new RunnerStagePolicy.Targets(null, null, null, A, Set.of(A));
            default -> RunnerStagePolicy.Targets.empty();
        };
    }

    private GitCommand command(String stageKey, CommandKind kind, RunnerStagePolicy.Targets targets) {
        String object = switch (kind) {
            case REVERT_NO_EDIT, REVERT_NO_COMMIT -> targets.revertTarget();
            case CHERRY_PICK -> targets.cherryPickTarget();
            case RESET_HARD -> targets.resetTarget();
            case CREATE_PAYMENT_RETRY_BRANCH, SWITCH_CREATE_PAYMENT_RETRY -> targets.recoveryTarget();
            default -> A;
        };
        String branch = switch (stageKey) {
            case "STAGE-GIT-02" -> "feature/profile";
            case "STAGE-GIT-03" -> "feature/search";
            default -> "feature/onboarding";
        };
        return new GitCommand(kind, object, branch);
    }

    private RepositorySnapshot stageTwo() {
        return new RepositorySnapshot(A, B, "", List.of(B), true, false, List.of(A, B),
                "feature/profile", A, B, false);
    }

    private RepositorySnapshot stageThree(boolean clean) {
        var state = new RepositorySnapshot.StageThreeState(A, B, A, C, List.of("search.txt"),
                List.of(), List.of(), List.of(), List.of());
        return snapshot(A, clean, "main", state, RepositorySnapshot.StageFourState.empty(),
                RepositorySnapshot.StageFiveState.empty(), RepositorySnapshot.TrainingState.empty());
    }

    private RepositorySnapshot stageFour(String mainTree) {
        var state = new RepositorySnapshot.StageFourState(A, B, C, B, mainTree, STAGE_FOUR_FEATURE_TREE,
                STAGE_FOUR_BLOB, List.of(), List.of(), List.of(), List.of());
        return snapshot(A, true, "main", RepositorySnapshot.StageThreeState.empty(), state,
                RepositorySnapshot.StageFiveState.empty(), RepositorySnapshot.TrainingState.empty());
    }

    private RepositorySnapshot stageFive(String paymentRetryTip) {
        var state = new RepositorySnapshot.StageFiveState(STAGE_FIVE_C0, STAGE_FIVE_C1, STAGE_FIVE_C0,
                STAGE_FIVE_TREE, paymentRetryTip, List.of("main"));
        return snapshot(STAGE_FIVE_C0, true, "main", RepositorySnapshot.StageThreeState.empty(),
                RepositorySnapshot.StageFourState.empty(), state, RepositorySnapshot.TrainingState.empty());
    }

    private RepositorySnapshot trainingOne(boolean clean) {
        var state = new RepositorySnapshot.TrainingState(A, null, List.of("onboarding/intro.txt"),
                List.of("onboarding/intro.txt"), List.of(), List.of(), List.of(), B, "", "", "", "", false);
        return training(clean, state);
    }

    private RepositorySnapshot trainingTwo(boolean clean) {
        var state = new RepositorySnapshot.TrainingState(A, null,
                List.of(".gitignore", "config/application-training.properties"),
                List.of(".gitignore", "config/application-training.properties"),
                List.of("build/training-report.txt"), List.of(), List.of(), "", B, C, A, "", true);
        return training(clean, state);
    }

    private RepositorySnapshot trainingThree(boolean clean) {
        var state = new RepositorySnapshot.TrainingState(A, null, List.of("docs/handoff.md"),
                List.of("docs/handoff.md"), List.of(), List.of(), List.of(), "", "", "", "", B, false);
        return training(clean, state);
    }

    private RepositorySnapshot training(boolean clean, RepositorySnapshot.TrainingState state) {
        return snapshot(A, clean, "main", RepositorySnapshot.StageThreeState.empty(),
                RepositorySnapshot.StageFourState.empty(), RepositorySnapshot.StageFiveState.empty(), state);
    }

    private RepositorySnapshot base(String head, boolean clean, String branch) {
        return snapshot(head, clean, branch, RepositorySnapshot.StageThreeState.empty(),
                RepositorySnapshot.StageFourState.empty(), RepositorySnapshot.StageFiveState.empty(),
                RepositorySnapshot.TrainingState.empty());
    }

    private RepositorySnapshot snapshot(String head, boolean clean, String branch,
            RepositorySnapshot.StageThreeState stageThree, RepositorySnapshot.StageFourState stageFour,
            RepositorySnapshot.StageFiveState stageFive, RepositorySnapshot.TrainingState training) {
        return new RepositorySnapshot(head, B, "", List.of(), clean, false, List.of(head), branch, "", "",
                false, false, false, stageThree, stageFour, stageFive, training);
    }
}
