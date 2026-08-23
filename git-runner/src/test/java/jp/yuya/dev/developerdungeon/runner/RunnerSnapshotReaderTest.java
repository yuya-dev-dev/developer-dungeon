package jp.yuya.dev.developerdungeon.runner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

class RunnerSnapshotReaderTest {
    private static final Duration TIMEOUT = Duration.ofSeconds(5);
    private static final String CONTAINER = "fixed-container";
    private static final String C0 = "0".repeat(40);
    private static final String C1 = "1".repeat(40);
    private static final String TREE = "2".repeat(40);

    @Test
    void readsTheBaseSnapshotWithOnlyFixedGitAndStateQueries() {
        DockerGateway docker = fixtureDocker();

        var snapshot = reader(docker).read(CONTAINER, "STAGE-GIT-01");

        assertThat(snapshot.headObjectId()).isEqualTo(C0);
        assertThat(snapshot.currentBranch()).isEqualTo("main");
        assertThat(snapshot.clean()).isTrue();
        assertThat(snapshot.ancestorObjectIds()).containsExactly(C0);
        verify(docker).run(argThat(arguments -> arguments.containsAll(List.of(
                "GIT_CONFIG_NOSYSTEM=1", "GIT_CONFIG_GLOBAL=/dev/null", "GIT_TERMINAL_PROMPT=0",
                "GIT_ALLOW_PROTOCOL=", "GIT_PROTOCOL_FROM_USER=0", "rev-list", "HEAD"))),
                org.mockito.ArgumentMatchers.eq(TIMEOUT));
    }

    @Test
    void rejectsNonZeroAndTruncatedGitOutputWithTheExistingMessage() {
        DockerGateway nonZero = fixtureDocker();
        when(nonZero.run(argThat(arguments -> arguments.contains("rev-list")), any(Duration.class)))
                .thenReturn(result(2, "", false));
        assertThatThrownBy(() -> reader(nonZero).read(CONTAINER, "STAGE-GIT-01"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("snapshot failed")
                .hasNoCause();

        DockerGateway truncated = fixtureDocker();
        when(truncated.run(argThat(arguments -> arguments.contains("rev-list")), any(Duration.class)))
                .thenReturn(result(0, C0, true));
        assertThatThrownBy(() -> reader(truncated).read(CONTAINER, "STAGE-GIT-01"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("snapshot failed")
                .hasNoCause();
    }

    @Test
    void failsClosedWhenAStateFileCheckCannotBeCompleted() {
        DockerGateway docker = fixtureDocker();
        when(docker.run(argThat(arguments -> arguments.contains("/workspace/.git/CHERRY_PICK_HEAD")), any(Duration.class)))
                .thenReturn(result(2, "", false));

        assertThatThrownBy(() -> reader(docker).read(CONTAINER, "STAGE-GIT-01"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Git state file check failed")
                .hasNoCause();
    }

    @Test
    void distinguishesAMissingBranchFromABrokenBranchQuery() {
        DockerGateway missing = fixtureDocker();
        assertThat(reader(missing).read(CONTAINER, "STAGE-GIT-05").stageFive().paymentRetryTip()).isNull();

        DockerGateway broken = fixtureDocker();
        when(broken.run(argThat(arguments -> arguments.contains("show-ref")), any(Duration.class)))
                .thenReturn(result(2, "", false));
        assertThatThrownBy(() -> reader(broken).read(CONTAINER, "STAGE-GIT-05"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("branch snapshot failed")
                .hasNoCause();
    }

    @Test
    void rejectsMalformedUnmergedPathsAndDuplicateStashObjects() {
        DockerGateway malformedPaths = fixtureDocker();
        when(malformedPaths.run(argThat(arguments -> arguments.contains("--unmerged")), any(Duration.class)))
                .thenReturn(result(0, "not-a-valid-unmerged-line", false));
        assertThatThrownBy(() -> reader(malformedPaths).read(CONTAINER, "STAGE-GIT-03"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("invalid unmerged path output")
                .hasNoCause();

        DockerGateway duplicateStash = fixtureDocker();
        when(duplicateStash.run(argThat(arguments -> arguments.contains("stash") && arguments.contains("--format=%H")), any(Duration.class)))
                .thenReturn(result(0, C0 + "\n" + C0 + "\n", false));
        assertThatThrownBy(() -> reader(duplicateStash).read(CONTAINER, "STAGE-GIT-03"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("invalid stash snapshot")
                .hasNoCause();
    }

    @Test
    void checksOnlyTheRequestedObjectTypeThroughTheFixedReader() {
        DockerGateway docker = fixtureDocker();
        when(docker.run(argThat(arguments -> arguments.contains("cat-file")), any(Duration.class)))
                .thenReturn(result(0, "commit\n", false));

        assertThat(reader(docker).isCommit(CONTAINER, C1)).isTrue();
        verify(docker).run(argThat(arguments -> arguments.containsAll(List.of("cat-file", "-t", C1))),
                org.mockito.ArgumentMatchers.eq(TIMEOUT));
    }

    private RunnerSnapshotReader reader(DockerGateway docker) {
        return new RunnerSnapshotReader(docker, TIMEOUT, "src/main/resources/messages.properties", C0, C1, TREE);
    }

    private DockerGateway fixtureDocker() {
        DockerGateway docker = mock(DockerGateway.class);
        when(docker.run(any(), any(Duration.class))).thenAnswer(invocation -> {
            List<String> arguments = invocation.getArgument(0);
            if (arguments.contains("/usr/bin/test")) return result(1, "", false);
            if (arguments.contains("show-ref")) return result(1, "", false);
            if (arguments.contains("stash") && arguments.contains("--format=%H")) return result(0, "", false);
            if (arguments.contains("rev-list")) return result(0, C0 + "\n", false);
            if (arguments.contains("status")) return result(0, "", false);
            if (arguments.contains("--show-current")) return result(0, "main\n", false);
            if (arguments.contains("--format=%P")) return result(0, "", false);
            if (arguments.contains("HEAD^{tree}")) return result(0, TREE + "\n", false);
            if (arguments.contains("hash-object")) return result(0, "3".repeat(40) + "\n", false);
            if (arguments.contains("for-each-ref") && arguments.contains("--format=%(refname:short)")) return result(0, "main\n", false);
            if (arguments.contains("diff") || arguments.contains("ls-files") || arguments.contains("ls-tree")) return result(0, "", false);
            if (arguments.contains("rev-parse")) return result(0, C0 + "\n", false);
            return result(0, "", false);
        });
        return docker;
    }

    private DockerGateway.ProcessResult result(int exitCode, String stdout, boolean truncated) {
        return new DockerGateway.ProcessResult(exitCode, stdout, "", truncated);
    }
}
