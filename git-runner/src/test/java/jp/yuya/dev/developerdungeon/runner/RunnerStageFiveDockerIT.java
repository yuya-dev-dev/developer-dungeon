package jp.yuya.dev.developerdungeon.runner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.util.UUID;
import jp.yuya.dev.developerdungeon.contract.CommandKind;
import jp.yuya.dev.developerdungeon.contract.DestroyRequest;
import jp.yuya.dev.developerdungeon.contract.ExecuteRequest;
import jp.yuya.dev.developerdungeon.contract.GitCommand;
import jp.yuya.dev.developerdungeon.contract.WorkspaceRequest;
import org.junit.jupiter.api.Test;

class RunnerStageFiveDockerIT {
    private static final String C0 = "4b03c129e4d5b2bfe41fb2afd208b13dab7824a1";
    private static final String C1 = "39194dda957695ace62387ecdc5f77fcd5ee81ea";
    private static final String TREE = "cbc2826a3bd49e4947b67c020e61dd5e4ca7adb3";

    @Test void recoversAfterStatusAndNormalLogThenTheFixedReflog() {
        RunnerWorkspaceService service = service();
        String attempt = UUID.randomUUID().toString();
        String workspaceId = null;
        try {
            var workspace = service.create(new WorkspaceRequest(attempt, id(1), "STAGE-GIT-05", 0));
            workspaceId = workspace.workspaceId();
            assertInitial(workspace.snapshot());

            assertThat(execute(service, attempt, workspaceId, 2, new GitCommand(CommandKind.STATUS)).exitCode()).isZero();
            assertThat(execute(service, attempt, workspaceId, 3, new GitCommand(CommandKind.LOG_ONELINE_ALL_DECORATE)).exitCode()).isZero();
            var reflog = execute(service, attempt, workspaceId, 4, new GitCommand(CommandKind.REFLOG_HEAD));
            assertThat(reflog.exitCode()).isZero();
            assertThat(reflog.stdout()).contains(C1.substring(0, 12) + "\tcommit: C1: payment retry");
            var branch = execute(service, attempt, workspaceId, 5, new GitCommand(CommandKind.CREATE_PAYMENT_RETRY_BRANCH, C1));
            assertThat(branch.snapshot().currentBranch()).isEqualTo("main");
            assertThat(branch.snapshot().stageFive().paymentRetryTip()).isEqualTo(C1);
            assertRecovered(execute(service, attempt, workspaceId, 6, new GitCommand(CommandKind.SWITCH_PAYMENT_RETRY)));
        } finally {
            destroy(service, attempt, workspaceId);
        }
    }

    @Test void recoversAfterReflogThenShowAndStatus() {
        RunnerWorkspaceService service = service();
        String attempt = UUID.randomUUID().toString();
        String workspaceId = null;
        try {
            var workspace = service.create(new WorkspaceRequest(attempt, id(10), "STAGE-GIT-05", 0));
            workspaceId = workspace.workspaceId();
            assertInitial(workspace.snapshot());
            var reflog = execute(service, attempt, workspaceId, 11, new GitCommand(CommandKind.REFLOG_HEAD));
            assertThat(reflog.exitCode()).isZero();
            assertThat(reflog.stdout()).contains(C1.substring(0, 12) + "\tcommit: C1: payment retry");
            assertThat(execute(service, attempt, workspaceId, 12, new GitCommand(CommandKind.SHOW, C1)).exitCode()).isZero();
            assertThat(execute(service, attempt, workspaceId, 13, new GitCommand(CommandKind.STATUS)).exitCode()).isZero();
            var branch = execute(service, attempt, workspaceId, 14, new GitCommand(CommandKind.CREATE_PAYMENT_RETRY_BRANCH, C1));
            assertThat(branch.snapshot().stageFive().paymentRetryTip()).isEqualTo(C1);
            assertRecovered(execute(service, attempt, workspaceId, 15, new GitCommand(CommandKind.SWITCH_PAYMENT_RETRY)));
        } finally {
            destroy(service, attempt, workspaceId);
        }
    }

    @Test void rejectsARecoveryBranchFromAnyOtherCommit() {
        RunnerWorkspaceService service = service();
        String attempt = UUID.randomUUID().toString();
        String workspaceId = null;
        try {
            workspaceId = service.create(new WorkspaceRequest(attempt, id(10), "STAGE-GIT-05", 0)).workspaceId();
            String id = workspaceId;
            assertThatThrownBy(() -> execute(service, attempt, id, 11,
                    new GitCommand(CommandKind.CREATE_PAYMENT_RETRY_BRANCH, C0))).isInstanceOf(IllegalArgumentException.class);
        } finally {
            destroy(service, attempt, workspaceId);
        }
    }

    private void assertRecovered(jp.yuya.dev.developerdungeon.contract.CommandResponse response) {
        assertThat(response.snapshot().currentBranch()).isEqualTo("feature/payment-retry");
        assertThat(response.snapshot().headObjectId()).isEqualTo(C1);
        assertThat(response.snapshot().headTreeId()).isEqualTo(TREE);
        assertThat(response.snapshot().clean()).isTrue();
        assertThat(response.snapshot().revertInProgress()).isFalse();
        assertThat(response.snapshot().cherryPickInProgress()).isFalse();
        assertThat(response.snapshot().mergeInProgress()).isFalse();
        assertThat(response.snapshot().rebaseInProgress()).isFalse();
        assertThat(response.snapshot().stageFive().mainTip()).isEqualTo(C0);
        assertThat(response.snapshot().stageFive().paymentRetryTip()).isEqualTo(C1);
        assertThat(response.snapshot().stageFive().localBranches()).containsExactly("feature/payment-retry", "main");
    }

    private void assertInitial(jp.yuya.dev.developerdungeon.contract.RepositorySnapshot snapshot) {
        assertThat(snapshot.currentBranch()).isEqualTo("main");
        assertThat(snapshot.headObjectId()).isEqualTo(C0);
        assertThat(snapshot.stageFive().recoveryTargetId()).isEqualTo(C1);
        assertThat(snapshot.stageFive().recoveryTargetTreeId()).isEqualTo(TREE);
        assertThat(snapshot.stageFive().paymentRetryTip()).isNull();
        assertThat(snapshot.stageFive().localBranches()).containsExactly("main");
    }

    private void destroy(RunnerWorkspaceService service, String attempt, String workspaceId) {
        if (workspaceId != null) try { service.destroy(new DestroyRequest(attempt, UUID.randomUUID().toString(), workspaceId, 0, "integration-test")); }
        catch (IllegalArgumentException ignored) { }
    }

    private RunnerWorkspaceService service() {
        RunnerProperties properties = new RunnerProperties("integration-test-token", required("DEVELOPER_DUNGEON_CHALLENGE_IMAGE_ID"),
                required("DEVELOPER_DUNGEON_CHALLENGE_IMAGE_FINGERPRINT"), required("DEVELOPER_DUNGEON_DOCKER_EXECUTABLE"));
        DockerGateway docker = new DockerGateway(properties);
        return new RunnerWorkspaceService(docker, properties, new RunnerCommandValidator(), Clock.systemUTC(), new MemoryContainerOwnershipLedger(Clock.systemUTC()));
    }
    private jp.yuya.dev.developerdungeon.contract.CommandResponse execute(RunnerWorkspaceService service, String attempt,
            String workspace, int request, GitCommand command) {
        return service.execute(new ExecuteRequest(attempt, id(request), workspace, 0, command));
    }
    private String required(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) value = System.getProperty(name);
        if (value == null || value.isBlank()) throw new IllegalStateException(name + " is required for RunnerStageFiveDockerIT");
        return value;
    }
    private static String id(int value) { return String.format("00000000-0000-0000-0000-%012d", value); }
}
