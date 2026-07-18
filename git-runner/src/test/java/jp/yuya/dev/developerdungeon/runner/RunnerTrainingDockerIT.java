package jp.yuya.dev.developerdungeon.runner;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.util.List;
import java.util.UUID;
import jp.yuya.dev.developerdungeon.contract.CommandKind;
import jp.yuya.dev.developerdungeon.contract.DestroyRequest;
import jp.yuya.dev.developerdungeon.contract.ExecuteRequest;
import jp.yuya.dev.developerdungeon.contract.GitCommand;
import jp.yuya.dev.developerdungeon.contract.WorkspaceRequest;
import org.junit.jupiter.api.Test;

class RunnerTrainingDockerIT {
    @Test void trainingOneRecordsThePreparedChangeOnMain() {
        run("TRAINING-GIT-01", 1, context -> {
            String initial = context.created().snapshot().training().mainTip();
            execute(context, 2, CommandKind.ADD_TRAINING_INTRO);
            var result = execute(context, 3, CommandKind.COMMIT_TRAINING_ONE).snapshot();

            assertThat(result.clean()).isTrue();
            assertThat(result.currentBranch()).isEqualTo("main");
            assertThat(result.headParents()).containsExactly(initial);
            assertThat(result.training().headPaths()).containsExactly("onboarding/intro.txt");
            assertThat(result.training().workingTreePaths()).isEmpty();
            assertThat(result.training().indexPaths()).isEmpty();
        });
    }

    @Test void trainingTwoUnstagesAndIgnoresTheGeneratedReport() {
        run("TRAINING-GIT-02", 10, context -> {
            String initial = context.created().snapshot().training().mainTip();
            execute(context, 11, CommandKind.UNSTAGE_TRAINING_REPORT);
            execute(context, 12, CommandKind.ADD_TRAINING_IGNORE);
            execute(context, 13, CommandKind.ADD_TRAINING_CONFIG);
            var result = execute(context, 14, CommandKind.COMMIT_TRAINING_TWO).snapshot();

            assertThat(result.clean()).isTrue();
            assertThat(result.headParents()).containsExactly(initial);
            assertThat(result.training().headPaths())
                    .containsExactly(".gitignore", "config/application-training.properties");
            assertThat(result.training().ignoredPaths()).containsExactly("build/training-report.txt");
            assertThat(result.training().reportExists()).isTrue();
        });
    }

    @Test void trainingThreeKeepsMainFixedAndCommitsOnTheNewBranch() {
        run("TRAINING-GIT-03", 20, context -> {
            String initial = context.created().snapshot().training().mainTip();
            execute(context, 21, CommandKind.SWITCH_CREATE_TRAINING_BRANCH);
            execute(context, 22, CommandKind.ADD_TRAINING_HANDOFF);
            var result = execute(context, 23, CommandKind.COMMIT_TRAINING_THREE).snapshot();

            assertThat(result.clean()).isTrue();
            assertThat(result.currentBranch()).isEqualTo("feature/onboarding");
            assertThat(result.headParents()).containsExactly(initial);
            assertThat(result.training().mainTip()).isEqualTo(initial);
            assertThat(result.training().trainingBranchTip()).isEqualTo(result.headObjectId());
            assertThat(result.training().headPaths()).containsExactly("docs/handoff.md");
        });
    }

    private void run(String stageKey, int seed, java.util.function.Consumer<TestContext> scenario) {
        RunnerWorkspaceService service = service();
        String attempt = UUID.randomUUID().toString();
        String workspace = null;
        try {
            var created = service.create(new WorkspaceRequest(attempt, id(seed), stageKey, 0));
            workspace = created.workspaceId();
            scenario.accept(new TestContext(service, attempt, workspace, created));
        } finally {
            if (workspace != null) {
                try {
                    service.destroy(new DestroyRequest(attempt, UUID.randomUUID().toString(), workspace, 0, "integration-test"));
                } catch (IllegalArgumentException ignored) { }
            }
        }
    }

    private jp.yuya.dev.developerdungeon.contract.CommandResponse execute(TestContext context, int request,
                                                                           CommandKind kind) {
        return context.service().execute(new ExecuteRequest(context.attempt(), id(request), context.workspace(), 0,
                new GitCommand(kind)));
    }

    private RunnerWorkspaceService service() {
        RunnerProperties properties = new RunnerProperties("integration-test-token",
                required("DEVELOPER_DUNGEON_CHALLENGE_IMAGE_ID"),
                required("DEVELOPER_DUNGEON_CHALLENGE_IMAGE_FINGERPRINT"),
                required("DEVELOPER_DUNGEON_DOCKER_EXECUTABLE"));
        Clock clock = Clock.systemUTC();
        return new RunnerWorkspaceService(new DockerGateway(properties), properties,
                new RunnerCommandValidator(), clock, new MemoryContainerOwnershipLedger(clock));
    }

    private String required(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) value = System.getProperty(name);
        if (value == null || value.isBlank()) throw new IllegalStateException(name + " is required for RunnerTrainingDockerIT");
        return value;
    }

    private static String id(int value) {
        return String.format("00000000-0000-0000-0000-%012d", value);
    }

    private record TestContext(RunnerWorkspaceService service, String attempt, String workspace,
                               jp.yuya.dev.developerdungeon.contract.WorkspaceResponse created) { }
}
