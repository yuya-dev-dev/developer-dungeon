package jp.yuya.dev.developerdungeon.runner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.util.List;
import java.util.UUID;
import jp.yuya.dev.developerdungeon.contract.CommandKind;
import jp.yuya.dev.developerdungeon.contract.DestroyRequest;
import jp.yuya.dev.developerdungeon.contract.ExecuteRequest;
import jp.yuya.dev.developerdungeon.contract.GitCommand;
import jp.yuya.dev.developerdungeon.contract.WorkspaceRequest;
import org.junit.jupiter.api.Test;

class RunnerStageThreeDockerIT {
    private static final String INITIAL_BLOB = "80b018f3f86a4e710347ea98c0b903a1c6fcd9e7";
    private static final String FINAL_BLOB = "0861e3929141f32f4e5c8bcd68fc03173a3e3c8e";

    @Test void createsTheFixedFixtureAndMovesOnlyTheStashedWorkToFeatureSearch() {
        RunnerProperties properties = new RunnerProperties("integration-test-token", required("DEVELOPER_DUNGEON_CHALLENGE_IMAGE_ID"),
                required("DEVELOPER_DUNGEON_CHALLENGE_IMAGE_FINGERPRINT"), required("DEVELOPER_DUNGEON_DOCKER_EXECUTABLE"));
        RunnerWorkspaceService service = new RunnerWorkspaceService(new DockerGateway(properties), properties, new RunnerCommandValidator(), Clock.systemUTC(), new MemoryContainerOwnershipLedger(Clock.systemUTC()));
        String attemptId = UUID.randomUUID().toString();
        String workspaceId = null;
        try {
            var workspace = service.create(new WorkspaceRequest(attemptId, UUID.randomUUID().toString(), "STAGE-GIT-03", 0));
            workspaceId = workspace.workspaceId();
            String activeWorkspaceId = workspaceId;
            var initial = workspace.snapshot();
            var initialState = initial.stageThree();
            String c0 = initial.headObjectId();
            String c1 = initialState.featureSearchTip();

            assertThat(initial.currentBranch()).isEqualTo("main");
            assertThat(initial.clean()).isFalse();
            assertThat(initialState.mainTip()).isEqualTo(c0);
            assertThat(initialState.featureSearchParent()).isEqualTo(c0);
            assertThat(initialState.searchFileBlobId()).isEqualTo(INITIAL_BLOB);
            assertThat(initialState.workingTreePaths()).containsExactly("search.txt");
            assertThat(initialState.indexPaths()).isEmpty();
            assertThat(initialState.unmergedPaths()).isEmpty();
            assertThat(initialState.untrackedPaths()).isEmpty();
            assertThat(initialState.stashObjectIds()).isEmpty();
            assertThat(initial.revertInProgress()).isFalse();
            assertThat(initial.cherryPickInProgress()).isFalse();
            assertThat(initial.mergeInProgress()).isFalse();
            assertThat(initial.rebaseInProgress()).isFalse();

            assertThatThrownBy(() -> execute(service, attemptId, activeWorkspaceId, 0, 1, GitCommand.switchTo("feature/profile")))
                    .isInstanceOf(IllegalArgumentException.class);

            var directSwitch = execute(service, attemptId, activeWorkspaceId, 0, 2, GitCommand.switchTo("feature/search"));
            assertThat(directSwitch.exitCode()).isNotZero();
            assertThat(directSwitch.snapshot().currentBranch()).isEqualTo("main");

            var stashed = execute(service, attemptId, activeWorkspaceId, 0, 3, new GitCommand(CommandKind.STASH_PUSH));
            assertThat(stashed.exitCode()).isZero();
            assertThat(stashed.snapshot().clean()).isTrue();
            assertThat(stashed.snapshot().stageThree().stashObjectIds()).isNotEmpty();

            var switched = execute(service, attemptId, activeWorkspaceId, 0, 4, GitCommand.switchTo("feature/search"));
            assertThat(switched.exitCode()).isZero();
            assertThat(switched.snapshot().currentBranch()).isEqualTo("feature/search");

            var restored = execute(service, attemptId, activeWorkspaceId, 0, 5, new GitCommand(CommandKind.STASH_POP));
            var finalState = restored.snapshot();
            assertThat(restored.exitCode()).isZero();
            assertThat(finalState.currentBranch()).isEqualTo("feature/search");
            assertThat(finalState.headObjectId()).isEqualTo(c1);
            assertThat(finalState.clean()).isFalse();
            assertThat(finalState.stageThree().mainTip()).isEqualTo(c0);
            assertThat(finalState.stageThree().featureSearchTip()).isEqualTo(c1);
            assertThat(finalState.stageThree().searchFileBlobId()).isEqualTo(FINAL_BLOB);
            assertThat(finalState.stageThree().workingTreePaths()).containsExactly("search.txt");
            assertThat(finalState.stageThree().indexPaths()).isEmpty();
            assertThat(finalState.stageThree().unmergedPaths()).isEmpty();
            assertThat(finalState.stageThree().untrackedPaths()).isEmpty();
            assertThat(finalState.stageThree().stashObjectIds()).isEmpty();
            assertThat(finalState.revertInProgress()).isFalse();
            assertThat(finalState.cherryPickInProgress()).isFalse();
            assertThat(finalState.mergeInProgress()).isFalse();
            assertThat(finalState.rebaseInProgress()).isFalse();
            assertThatThrownBy(() -> execute(service, attemptId, activeWorkspaceId, 0, 6, new GitCommand(CommandKind.STASH_PUSH, "a".repeat(40))))
                    .isInstanceOf(IllegalArgumentException.class);
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
        if (value == null || value.isBlank()) throw new IllegalStateException(name + " is required for RunnerStageThreeDockerIT");
        return value;
    }
}
