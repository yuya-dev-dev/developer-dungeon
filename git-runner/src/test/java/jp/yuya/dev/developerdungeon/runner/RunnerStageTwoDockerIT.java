package jp.yuya.dev.developerdungeon.runner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import jp.yuya.dev.developerdungeon.contract.CommandKind;
import jp.yuya.dev.developerdungeon.contract.DestroyRequest;
import jp.yuya.dev.developerdungeon.contract.ExecuteRequest;
import jp.yuya.dev.developerdungeon.contract.GitCommand;
import jp.yuya.dev.developerdungeon.contract.WorkspaceRequest;
import org.junit.jupiter.api.Test;

class RunnerStageTwoDockerIT {
    private static final Duration TIMEOUT = Duration.ofSeconds(5);

    @Test
    void createsTheFixedFixtureAndClearsStageTwoWithOnlyAllowedTargets() {
        RunnerProperties properties = new RunnerProperties("integration-test-token", required("DEVELOPER_DUNGEON_CHALLENGE_IMAGE_ID"),
                required("DEVELOPER_DUNGEON_CHALLENGE_IMAGE_FINGERPRINT"), required("DEVELOPER_DUNGEON_DOCKER_EXECUTABLE"));
        DockerGateway docker = new DockerGateway(properties);
        RunnerWorkspaceService service = new RunnerWorkspaceService(docker, properties, new RunnerCommandValidator(), Clock.systemUTC(), new MemoryContainerOwnershipLedger(Clock.systemUTC()));
        String attemptId = UUID.randomUUID().toString();
        String workspaceId = null;
        try {
            var workspace = service.create(new WorkspaceRequest(attemptId, UUID.randomUUID().toString(), "STAGE-GIT-02", 0));
            workspaceId = workspace.workspaceId();
            String activeWorkspaceId = workspaceId;
            var initial = workspace.snapshot();
            String c1 = initial.headObjectId();
            String c0 = initial.featureNotificationTip();
            assertThat(initial.currentBranch()).isEqualTo("feature/profile");
            assertThat(initial.featureProfileTip()).isEqualTo(c1);
            assertThat(initial.headParents()).containsExactly(c0);
            assertThat(c0).hasSize(40);
            assertThat(c1).hasSize(40).isNotEqualTo(c0);
            assertThat(c0.substring(0, 12)).isNotEqualTo(c1.substring(0, 12));
            assertThat(initial.clean()).isTrue();
            assertThat(initial.cherryPickInProgress()).isFalse();

            assertThatThrownBy(() -> execute(service, attemptId, activeWorkspaceId, 0, 90, new GitCommand(CommandKind.CHERRY_PICK, c0))).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> execute(service, attemptId, activeWorkspaceId, 0, 91, new GitCommand(CommandKind.RESET_HARD, c1))).isInstanceOf(IllegalArgumentException.class);

            execute(service, attemptId, workspaceId, 0, 1, GitCommand.switchTo("feature/notification"));
            execute(service, attemptId, workspaceId, 0, 2, new GitCommand(CommandKind.CHERRY_PICK, c1));
            execute(service, attemptId, workspaceId, 0, 3, GitCommand.switchTo("feature/profile"));
            execute(service, attemptId, workspaceId, 0, 4, new GitCommand(CommandKind.RESET_HARD, c0));
            var finalResponse = execute(service, attemptId, workspaceId, 0, 5, GitCommand.switchTo("feature/notification"));

            var cleared = finalResponse.snapshot();
            assertThat(cleared.currentBranch()).isEqualTo("feature/notification");
            assertThat(cleared.featureProfileTip()).isEqualTo(c0);
            assertThat(cleared.featureNotificationTip()).isEqualTo(cleared.headObjectId()).isNotEqualTo(c0);
            assertThat(cleared.headTreeId()).isEqualTo(initial.headTreeId());
            assertThat(cleared.headParents()).containsExactly(c0);
            assertThat(cleared.clean()).isTrue();
            assertThat(cleared.cherryPickInProgress()).isFalse();
        } finally {
            if (workspaceId != null) service.destroy(new DestroyRequest(attemptId, UUID.randomUUID().toString(), workspaceId, 0, "integration-test"));
        }
    }

    private jp.yuya.dev.developerdungeon.contract.CommandResponse execute(RunnerWorkspaceService service, String attemptId, String workspaceId,
                                                                            long generation, int request, GitCommand command) {
        return service.execute(new ExecuteRequest(attemptId, String.format("00000000-0000-0000-0000-%012d", request), workspaceId, generation, command));
    }
    private String required(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) throw new IllegalStateException(name + " is required for RunnerStageTwoDockerIT");
        return value;
    }
}
