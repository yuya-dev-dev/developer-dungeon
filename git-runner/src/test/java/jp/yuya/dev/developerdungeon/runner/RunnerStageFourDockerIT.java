package jp.yuya.dev.developerdungeon.runner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Field;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiConsumer;
import jp.yuya.dev.developerdungeon.contract.CommandKind;
import jp.yuya.dev.developerdungeon.contract.DestroyRequest;
import jp.yuya.dev.developerdungeon.contract.ExecuteRequest;
import jp.yuya.dev.developerdungeon.contract.GitCommand;
import jp.yuya.dev.developerdungeon.contract.ReadFileRequest;
import jp.yuya.dev.developerdungeon.contract.StageFileKey;
import jp.yuya.dev.developerdungeon.contract.WorkspaceRequest;
import jp.yuya.dev.developerdungeon.contract.WriteFileRequest;
import org.junit.jupiter.api.Test;

class RunnerStageFourDockerIT {
    private static final Duration TIMEOUT = Duration.ofSeconds(5);
    private static final String PATH = "/workspace/src/main/resources/messages.properties";
    private static final String MAIN_BLOB = "a6306bacd230ac74aaf017cde7717bc3eb83684c";
    private static final String FINAL_BLOB = "e9de6755c74ff6bee8b96abcccfec27a06f23881";
    private static final String MAIN_TREE = "63ec3ef493c5b54618798e50fe8d2e58bc40a4a9";
    private static final String FEATURE_TREE = "e4d8a76dfb74d699e48a7437d60811202ba7face";
    private static final String FINAL_TREE = "0c0ff72db8de95d04ed1169388a4f345c870d686";
    private static final String WRONG = "profile.description=Only one requirement.\n";
    private static final String EXPECTED = "profile.description=Manage security settings and edit your public profile.\n";

    @Test void resolvesTheFixedConflictRejectsStaleWritesAndAllowsEditingAfterAdd() {
        TestContext context = context();
        String attemptId = UUID.randomUUID().toString();
        String workspaceId = null;
        try {
            var workspace = context.service.create(new WorkspaceRequest(attemptId, id(1), "STAGE-GIT-04", 0));
            workspaceId = workspace.workspaceId();
            var initial = workspace.snapshot();
            String c1 = initial.headObjectId();
            String c2 = initial.stageFour().featureProfileMessageTip();
            assertThat(initial.currentBranch()).isEqualTo("main");
            assertThat(initial.clean()).isTrue();
            assertThat(initial.stageFour().messagesBlobId()).isEqualTo(MAIN_BLOB);
            assertThat(initial.stageFour().mainTreeId()).isEqualTo(MAIN_TREE);
            assertThat(initial.stageFour().featureTreeId()).isEqualTo(FEATURE_TREE);

            var merge = execute(context.service, attemptId, workspaceId, 2, new GitCommand(CommandKind.MERGE_PROFILE_MESSAGE));
            assertThat(merge.exitCode()).isNotZero();
            assertThat(merge.snapshot().mergeInProgress()).isTrue();
            assertThat(merge.snapshot().stageFour().unmergedPaths()).containsExactly("src/main/resources/messages.properties");

            var original = read(context.service, attemptId, workspaceId, 3);
            assertThat(original.content()).contains("<<<<<<<", "Manage security settings.", "Edit your public profile.", ">>>>>>>");

            WriteFileRequest firstWrite = writeRequest(attemptId, workspaceId, 4, WRONG, original.versionToken());
            var wrong = context.service.writeFile(firstWrite);
            assertThat(wrong.written()).isTrue();
            assertThat(context.service.writeFile(firstWrite)).isSameAs(wrong);

            var stale = context.service.writeFile(writeRequest(attemptId, workspaceId, 5, EXPECTED, original.versionToken()));
            assertThat(stale.written()).isFalse();
            assertThat(read(context.service, attemptId, workspaceId, 6).content()).isEqualTo(WRONG);

            var firstAdd = execute(context.service, attemptId, workspaceId, 7, new GitCommand(CommandKind.ADD_PROFILE_MESSAGES));
            assertThat(firstAdd.exitCode()).isZero();
            assertThat(firstAdd.snapshot().mergeInProgress()).isTrue();
            assertThat(firstAdd.snapshot().stageFour().unmergedPaths()).isEmpty();

            var afterAdd = read(context.service, attemptId, workspaceId, 8);
            var corrected = context.service.writeFile(writeRequest(attemptId, workspaceId, 9, EXPECTED, afterAdd.versionToken()));
            assertThat(corrected.written()).isTrue();
            assertThat(corrected.snapshot().mergeInProgress()).isTrue();
            execute(context.service, attemptId, workspaceId, 10, new GitCommand(CommandKind.ADD_PROFILE_MESSAGES));
            var commit = execute(context.service, attemptId, workspaceId, 11, new GitCommand(CommandKind.COMMIT_NO_EDIT));

            var result = commit.snapshot();
            assertThat(commit.exitCode()).isZero();
            assertThat(result.currentBranch()).isEqualTo("main");
            assertThat(result.headParents()).containsExactly(c1, c2);
            assertThat(result.headTreeId()).isEqualTo(FINAL_TREE);
            assertThat(result.stageFour().messagesBlobId()).isEqualTo(FINAL_BLOB);
            assertThat(result.stageFour().featureProfileMessageTip()).isEqualTo(c2);
            assertThat(result.clean()).isTrue();
            assertThat(result.mergeInProgress()).isFalse();
            assertThat(result.stageFour().workingTreePaths()).isEmpty();
            assertThat(result.stageFour().indexPaths()).isEmpty();
            assertThat(result.stageFour().unmergedPaths()).isEmpty();
            assertThat(result.stageFour().untrackedPaths()).isEmpty();
        } finally {
            destroyIfPresent(context.service, attemptId, workspaceId);
            assertFileOperationResponsesDiscarded(context.service);
        }
    }

    @Test void rejectsEveryUnsafeFixedPathShapeAndDiscardsTheWorkspace() {
        assertUnsafePath((docker, container) -> {
            run(docker, List.of("exec", container, "/bin/rm", PATH));
            run(docker, List.of("exec", container, "/bin/ln", "-s", "/workspace/.git/HEAD", PATH));
        });
        assertUnsafePath((docker, container) -> {
            run(docker, List.of("exec", container, "/bin/mv", "/workspace/src/main/resources", "/workspace/.git/resources-real"));
            run(docker, List.of("exec", container, "/bin/ln", "-s", "/workspace/.git/resources-real", "/workspace/src/main/resources"));
        });
        assertUnsafePath((docker, container) ->
                run(docker, List.of("exec", container, "/bin/ln", PATH, "/workspace/.git/messages-hard-link")));
        assertUnsafePath((docker, container) -> {
            run(docker, List.of("exec", container, "/bin/rm", PATH));
            run(docker, List.of("exec", container, "/bin/mkdir", PATH));
        });
    }

    private void assertUnsafePath(BiConsumer<DockerGateway, String> corrupt) {
        TestContext context = context();
        String attemptId = UUID.randomUUID().toString();
        var workspace = context.service.create(new WorkspaceRequest(attemptId, UUID.randomUUID().toString(), "STAGE-GIT-04", 0));
        String workspaceId = workspace.workspaceId();
        execute(context.service, attemptId, workspaceId, 20, new GitCommand(CommandKind.MERGE_PROFILE_MESSAGE));
        var file = read(context.service, attemptId, workspaceId, 21);
        String container = containerFor(context.docker, workspaceId);
        corrupt.accept(context.docker, container);

        assertThatThrownBy(() -> context.service.writeFile(writeRequest(attemptId, workspaceId, 22, EXPECTED, file.versionToken())))
                .isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> context.service.snapshotFor(workspaceId, attemptId, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertFileOperationResponsesDiscarded(context.service);
    }

    private TestContext context() {
        RunnerProperties properties = new RunnerProperties("integration-test-token", required("DEVELOPER_DUNGEON_CHALLENGE_IMAGE_ID"),
                required("DEVELOPER_DUNGEON_CHALLENGE_IMAGE_FINGERPRINT"), required("DEVELOPER_DUNGEON_DOCKER_EXECUTABLE"));
        DockerGateway docker = new DockerGateway(properties);
        RunnerWorkspaceService service = new RunnerWorkspaceService(docker, properties, new RunnerCommandValidator(),
                Clock.systemUTC(), new MemoryContainerOwnershipLedger(Clock.systemUTC()));
        return new TestContext(docker, service);
    }
    private jp.yuya.dev.developerdungeon.contract.CommandResponse execute(RunnerWorkspaceService service, String attempt,
            String workspace, int request, GitCommand command) {
        return service.execute(new ExecuteRequest(attempt, id(request), workspace, 0, command));
    }
    private jp.yuya.dev.developerdungeon.contract.FileContentResponse read(RunnerWorkspaceService service, String attempt,
            String workspace, int request) {
        return service.readFile(new ReadFileRequest(attempt, id(request), workspace, 0, StageFileKey.PROFILE_MESSAGES));
    }
    private WriteFileRequest writeRequest(String attempt, String workspace, int request, String content, String token) {
        return new WriteFileRequest(attempt, id(request), workspace, 0, StageFileKey.PROFILE_MESSAGES, content, token);
    }
    private String containerFor(DockerGateway docker, String workspaceId) {
        var result = docker.run(List.of("ps", "--filter", "label=io.developer-dungeon.workspace=" + workspaceId,
                "--format", "{{.ID}}"), TIMEOUT);
        assertThat(result.exitCode()).isZero();
        assertThat(result.stdout().trim()).matches("[0-9a-f]{12,64}");
        return result.stdout().trim();
    }
    private void run(DockerGateway docker, List<String> arguments) {
        var result = docker.run(arguments, TIMEOUT);
        assertThat(result.exitCode()).as(arguments.toString()).isZero();
    }
    private void destroyIfPresent(RunnerWorkspaceService service, String attempt, String workspace) {
        if (workspace == null) return;
        try { service.destroy(new DestroyRequest(attempt, UUID.randomUUID().toString(), workspace, 0, "integration-test")); }
        catch (IllegalArgumentException ignored) { }
    }
    private void assertFileOperationResponsesDiscarded(RunnerWorkspaceService service) {
        assertThat(cacheSize(service, "readFileRequests")).isZero();
        assertThat(cacheSize(service, "writeFileRequests")).isZero();
    }
    private int cacheSize(RunnerWorkspaceService service, String fieldName) {
        try {
            Field field = RunnerWorkspaceService.class.getDeclaredField(fieldName);
            field.setAccessible(true);
            return ((Map<?, ?>) field.get(service)).size();
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("file operation cache is unavailable", exception);
        }
    }
    private String required(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) throw new IllegalStateException(name + " is required for RunnerStageFourDockerIT");
        return value;
    }
    private static String id(int value) { return String.format("00000000-0000-0000-0000-%012d", value); }
    private record TestContext(DockerGateway docker, RunnerWorkspaceService service) { }
}
