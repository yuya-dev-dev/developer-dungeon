package jp.yuya.dev.developerdungeon.runner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.util.UUID;
import jp.yuya.dev.developerdungeon.contract.CommandKind;
import jp.yuya.dev.developerdungeon.contract.DestroyRequest;
import jp.yuya.dev.developerdungeon.contract.ExecuteRequest;
import jp.yuya.dev.developerdungeon.contract.GitCommand;
import jp.yuya.dev.developerdungeon.contract.ReadFileRequest;
import jp.yuya.dev.developerdungeon.contract.StageFileKey;
import jp.yuya.dev.developerdungeon.contract.WorkspaceRequest;
import jp.yuya.dev.developerdungeon.contract.WriteFileRequest;
import org.junit.jupiter.api.Test;

class RunnerAlternativeSolutionsDockerIT {
    private static final String STAGE_FOUR_CONTENT =
            "profile.description=Manage security settings and edit your public profile.\n";
    private static final String STAGE_FOUR_EXPECTED_BLOB = "e9de6755c74ff6bee8b96abcccfec27a06f23881";

    @Test void stageOneClearsThroughNoCommitRevertAndFixedCommit() {
        TestContext context = context();
        String attempt = UUID.randomUUID().toString();
        String workspace = null;
        try {
            var created = context.service().create(new WorkspaceRequest(attempt, id(1), "STAGE-GIT-01", 0));
            workspace = created.workspaceId();
            var initial = created.snapshot();
            var staged = execute(context.service(), attempt, workspace, 2,
                    new GitCommand(CommandKind.REVERT_NO_COMMIT, initial.headObjectId()));
            assertThat(staged.exitCode()).isZero();
            assertThat(staged.snapshot().clean()).isFalse();

            var restored = execute(context.service(), attempt, workspace, 3,
                    new GitCommand(CommandKind.COMMIT_RESTORE_SETTINGS));
            assertThat(restored.exitCode()).isZero();
            assertThat(restored.snapshot().clean()).isTrue();
            assertThat(restored.snapshot().headTreeId()).isEqualTo(initial.firstParentTreeId());
            assertThat(restored.snapshot().headParents()).containsExactly(initial.headObjectId());
            var log = execute(context.service(), attempt, workspace, 4, new GitCommand(CommandKind.LOG_ONELINE));
            assertThat(log.stdout()).contains("restore-required-settings");
        } finally {
            destroy(context.service(), attempt, workspace);
        }
    }

    @Test void stageTwoClearsWhenResetRunsBeforeCherryPick() {
        TestContext context = context();
        String attempt = UUID.randomUUID().toString();
        String workspace = null;
        try {
            var created = context.service().create(new WorkspaceRequest(attempt, id(10), "STAGE-GIT-02", 0));
            workspace = created.workspaceId();
            var initial = created.snapshot();
            String c1 = initial.headObjectId();
            String c0 = initial.featureNotificationTip();

            assertThat(execute(context.service(), attempt, workspace, 11,
                    new GitCommand(CommandKind.RESET_HARD, c0)).exitCode()).isZero();
            assertThat(execute(context.service(), attempt, workspace, 12,
                    GitCommand.switchTo("feature/notification")).exitCode()).isZero();
            var moved = execute(context.service(), attempt, workspace, 13,
                    new GitCommand(CommandKind.CHERRY_PICK, c1));

            assertThat(moved.exitCode()).isZero();
            assertThat(moved.snapshot().currentBranch()).isEqualTo("feature/notification");
            assertThat(moved.snapshot().featureProfileTip()).isEqualTo(c0);
            assertThat(moved.snapshot().headParents()).containsExactly(c0);
            assertThat(moved.snapshot().headTreeId()).isEqualTo(initial.headTreeId());
        } finally {
            destroy(context.service(), attempt, workspace);
        }
    }

    @Test void stageThreeClearsThroughApplyAndDrop() {
        TestContext context = context();
        String attempt = UUID.randomUUID().toString();
        String workspace = null;
        try {
            workspace = context.service().create(new WorkspaceRequest(attempt, id(20), "STAGE-GIT-03", 0)).workspaceId();
            execute(context.service(), attempt, workspace, 21, new GitCommand(CommandKind.STASH_PUSH));
            execute(context.service(), attempt, workspace, 22, GitCommand.switchTo("feature/search"));
            var applied = execute(context.service(), attempt, workspace, 23, new GitCommand(CommandKind.STASH_APPLY));
            assertThat(applied.exitCode()).isZero();
            assertThat(applied.snapshot().stageThree().workingTreePaths()).containsExactly("search.txt");
            assertThat(applied.snapshot().stageThree().stashObjectIds()).isNotEmpty();

            var dropped = execute(context.service(), attempt, workspace, 24, new GitCommand(CommandKind.STASH_DROP));
            assertThat(dropped.exitCode()).isZero();
            assertThat(dropped.snapshot().currentBranch()).isEqualTo("feature/search");
            assertThat(dropped.snapshot().stageThree().workingTreePaths()).containsExactly("search.txt");
            assertThat(dropped.snapshot().stageThree().stashObjectIds()).isEmpty();
        } finally {
            destroy(context.service(), attempt, workspace);
        }
    }

    @Test void stageFourClearsThroughCommitAllAfterTheLimitedEditor() {
        TestContext context = context();
        String attempt = UUID.randomUUID().toString();
        String workspace = null;
        try {
            workspace = context.service().create(new WorkspaceRequest(attempt, id(30), "STAGE-GIT-04", 0)).workspaceId();
            var merge = execute(context.service(), attempt, workspace, 31,
                    new GitCommand(CommandKind.MERGE_PROFILE_MESSAGE));
            assertThat(merge.snapshot().mergeInProgress()).isTrue();
            var file = context.service().readFile(new ReadFileRequest(attempt, id(32), workspace, 0,
                    StageFileKey.PROFILE_MESSAGES));
            var edited = context.service().writeFile(new WriteFileRequest(attempt, id(33), workspace, 0,
                    StageFileKey.PROFILE_MESSAGES, STAGE_FOUR_CONTENT, file.versionToken()));
            assertThat(edited.written()).isTrue();

            var committed = execute(context.service(), attempt, workspace, 34,
                    new GitCommand(CommandKind.COMMIT_ALL_NO_EDIT));
            assertThat(committed.exitCode()).isZero();
            assertThat(committed.snapshot().clean()).isTrue();
            assertThat(committed.snapshot().mergeInProgress()).isFalse();
            assertThat(committed.snapshot().headParents()).hasSize(2);
            assertThat(committed.snapshot().stageFour().unmergedPaths()).isEmpty();
        } finally {
            destroy(context.service(), attempt, workspace);
        }
    }

    @Test void stageFourCommitAllWithoutEditingProducesAStateThatCannotClear() {
        TestContext context = context();
        String attempt = UUID.randomUUID().toString();
        String workspace = null;
        try {
            workspace = context.service().create(new WorkspaceRequest(attempt, id(35), "STAGE-GIT-04", 0)).workspaceId();
            var merge = execute(context.service(), attempt, workspace, 36,
                    new GitCommand(CommandKind.MERGE_PROFILE_MESSAGE));
            assertThat(merge.snapshot().mergeInProgress()).isTrue();

            var committed = execute(context.service(), attempt, workspace, 37,
                    new GitCommand(CommandKind.COMMIT_ALL_NO_EDIT));
            assertThat(committed.exitCode()).isZero();
            assertThat(committed.snapshot().clean()).isTrue();
            assertThat(committed.snapshot().mergeInProgress()).isFalse();
            assertThat(committed.snapshot().stageFour().unmergedPaths()).isEmpty();
            assertThat(committed.snapshot().stageFour().messagesBlobId())
                    .isNotEqualTo(STAGE_FOUR_EXPECTED_BLOB);
        } finally {
            destroy(context.service(), attempt, workspace);
        }
    }

    @Test void stageFiveClearsThroughSwitchCreate() {
        TestContext context = context();
        String attempt = UUID.randomUUID().toString();
        String workspace = null;
        try {
            var created = context.service().create(new WorkspaceRequest(attempt, id(40), "STAGE-GIT-05", 0));
            workspace = created.workspaceId();
            String recovery = created.snapshot().stageFive().recoveryTargetId();
            var restored = execute(context.service(), attempt, workspace, 41,
                    new GitCommand(CommandKind.SWITCH_CREATE_PAYMENT_RETRY, recovery));

            assertThat(restored.exitCode()).isZero();
            assertThat(restored.snapshot().currentBranch()).isEqualTo("feature/payment-retry");
            assertThat(restored.snapshot().headObjectId()).isEqualTo(recovery);
            assertThat(restored.snapshot().stageFive().paymentRetryTip()).isEqualTo(recovery);
            assertThat(restored.snapshot().stageFive().localBranches()).containsExactly("feature/payment-retry", "main");
        } finally {
            destroy(context.service(), attempt, workspace);
        }
    }

    @Test void stageFiveRejectsSwitchCreateFromMainTip() {
        TestContext context = context();
        String attempt = UUID.randomUUID().toString();
        String workspace = null;
        try {
            var created = context.service().create(new WorkspaceRequest(attempt, id(42), "STAGE-GIT-05", 0));
            workspace = created.workspaceId();
            String activeWorkspace = workspace;
            assertThatThrownBy(() -> execute(context.service(), attempt, activeWorkspace, 43,
                    new GitCommand(CommandKind.SWITCH_CREATE_PAYMENT_RETRY, created.snapshot().stageFive().mainTip())))
                    .isInstanceOf(IllegalArgumentException.class);
        } finally {
            destroy(context.service(), attempt, workspace);
        }
    }

    private TestContext context() {
        RunnerProperties properties = new RunnerProperties("integration-test-token",
                required("DEVELOPER_DUNGEON_CHALLENGE_IMAGE_ID"),
                required("DEVELOPER_DUNGEON_CHALLENGE_IMAGE_FINGERPRINT"),
                required("DEVELOPER_DUNGEON_DOCKER_EXECUTABLE"));
        return new TestContext(new RunnerWorkspaceService(new DockerGateway(properties), properties,
                new RunnerCommandValidator(), Clock.systemUTC(), new MemoryContainerOwnershipLedger(Clock.systemUTC())));
    }

    private jp.yuya.dev.developerdungeon.contract.CommandResponse execute(RunnerWorkspaceService service,
            String attempt, String workspace, int request, GitCommand command) {
        return service.execute(new ExecuteRequest(attempt, id(request), workspace, 0, command));
    }

    private void destroy(RunnerWorkspaceService service, String attempt, String workspace) {
        if (workspace == null) return;
        try {
            service.destroy(new DestroyRequest(attempt, UUID.randomUUID().toString(), workspace, 0, "integration-test"));
        } catch (IllegalArgumentException ignored) { }
    }

    private String required(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) value = System.getProperty(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " is required for RunnerAlternativeSolutionsDockerIT");
        }
        return value;
    }

    private static String id(int value) {
        return String.format("00000000-0000-0000-0000-%012d", value);
    }

    private record TestContext(RunnerWorkspaceService service) { }
}
