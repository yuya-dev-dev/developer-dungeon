package jp.yuya.dev.developerdungeon.runner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Test;
import jp.yuya.dev.developerdungeon.contract.CommandKind;
import jp.yuya.dev.developerdungeon.contract.ExecuteRequest;
import jp.yuya.dev.developerdungeon.contract.GitCommand;
import jp.yuya.dev.developerdungeon.contract.WorkspaceRequest;

class RunnerWorkspaceServiceIdempotencyTest {
    private static final String IMAGE = "sha256:" + "a".repeat(64);
    private static final String FINGERPRINT = "b".repeat(64);
    private static final String CONTAINER = "c".repeat(64);

    @Test
    void duplicateCreateRequestUsesTheExistingWorkspace() {
        DockerGateway docker = fixtureDocker();
        var service = new RunnerWorkspaceService(docker, new RunnerProperties("a".repeat(43), IMAGE, FINGERPRINT, "C:\\docker.exe"), new RunnerCommandValidator());
        var request = new WorkspaceRequest("11111111-1111-1111-1111-111111111111", "22222222-2222-2222-2222-222222222222", "STAGE-GIT-01", 0);

        var first = service.create(request);
        var second = service.create(request);

        assertThat(second).isSameAs(first);
        verify(docker, times(1)).run(argThat(arguments -> arguments.size() > 1 && arguments.getFirst().equals("run") && arguments.contains("-d")), any(Duration.class));
    }

    @Test
    void rejectsInvalidRequestIdentityBeforeCallingDocker() {
        DockerGateway docker = mock(DockerGateway.class);
        var service = new RunnerWorkspaceService(docker, new RunnerProperties("a".repeat(43), IMAGE, FINGERPRINT, "C:\\docker.exe"), new RunnerCommandValidator());

        assertThatThrownBy(() -> service.create(new WorkspaceRequest("not-a-uuid", "22222222-2222-2222-2222-222222222222", "STAGE-GIT-01", 0)))
                .isInstanceOf(IllegalArgumentException.class);
        verify(docker, times(0)).run(any(), any(Duration.class));
    }

    @Test
    void removesContainerExactlyOnceWhenContainerIdentityVerificationFails() {
        DockerGateway docker = fixtureDocker();
        when(docker.run(argThat(arguments -> arguments.getFirst().equals("container")), any(Duration.class)))
                .thenReturn(result(IMAGE + "|{}"));
        var service = new RunnerWorkspaceService(docker, new RunnerProperties("a".repeat(43), IMAGE, FINGERPRINT, "C:\\docker.exe"), new RunnerCommandValidator());

        assertThatThrownBy(() -> service.create(new WorkspaceRequest("11111111-1111-1111-1111-111111111111", "22222222-2222-2222-2222-222222222222", "STAGE-GIT-01", 0)))
                .isInstanceOf(IllegalStateException.class);

        verify(docker, times(1)).run(argThat(arguments -> arguments.getFirst().equals("rm")), any(Duration.class));
    }

    @Test
    void rejectsRevertOfAnAncestorOtherThanTheAccidentalCommit() {
        DockerGateway docker = fixtureDocker();
        var service = new RunnerWorkspaceService(docker, new RunnerProperties("a".repeat(43), IMAGE, FINGERPRINT, "C:\\docker.exe"), new RunnerCommandValidator());
        var workspace = service.create(new WorkspaceRequest("11111111-1111-1111-1111-111111111111", "22222222-2222-2222-2222-222222222222", "STAGE-GIT-01", 0));

        assertThatThrownBy(() -> service.execute(new ExecuteRequest("11111111-1111-1111-1111-111111111111", "33333333-3333-3333-3333-333333333333", workspace.workspaceId(), 0,
                new GitCommand(CommandKind.REVERT_NO_EDIT, List.of("b".repeat(40)))))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void runsGitWithFixedConfigurationAndNoExternalDiffOrTextConversion() {
        DockerGateway docker = fixtureDocker();
        var service = new RunnerWorkspaceService(docker, new RunnerProperties("a".repeat(43), IMAGE, FINGERPRINT, "C:\\docker.exe"), new RunnerCommandValidator());
        var workspace = service.create(new WorkspaceRequest("11111111-1111-1111-1111-111111111111", "22222222-2222-2222-2222-222222222222", "STAGE-GIT-01", 0));

        service.execute(new ExecuteRequest("11111111-1111-1111-1111-111111111111", "33333333-3333-3333-3333-333333333333", workspace.workspaceId(), 0,
                new GitCommand(CommandKind.SHOW, List.of("c".repeat(40)))));

        verify(docker).run(argThat(arguments -> arguments.containsAll(List.of("GIT_CONFIG_NOSYSTEM=1", "GIT_CONFIG_GLOBAL=/dev/null", "GIT_TERMINAL_PROMPT=0", "--no-ext-diff", "--no-textconv"))), any(Duration.class));
    }

    @Test
    void duplicateExecuteRequestRunsGitOnlyOnce() {
        DockerGateway docker = fixtureDocker();
        var service = new RunnerWorkspaceService(docker, new RunnerProperties("a".repeat(43), IMAGE, FINGERPRINT, "C:\\docker.exe"), new RunnerCommandValidator());
        var workspace = service.create(new WorkspaceRequest("11111111-1111-1111-1111-111111111111", "22222222-2222-2222-2222-222222222222", "STAGE-GIT-01", 0));
        var request = new ExecuteRequest("11111111-1111-1111-1111-111111111111", "33333333-3333-3333-3333-333333333333", workspace.workspaceId(), 0,
                new GitCommand(CommandKind.SHOW, List.of("c".repeat(40))));

        service.execute(request);
        service.execute(request);

        verify(docker, times(1)).run(argThat(arguments -> arguments.contains("--no-ext-diff")), any(Duration.class));
    }

    @Test
    void expiresWorkspaceAfterFifteenMinutesAndRejectsLaterOperations() {
        DockerGateway docker = fixtureDocker();
        MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
        var service = new RunnerWorkspaceService(docker, new RunnerProperties("a".repeat(43), IMAGE, FINGERPRINT, "C:\\docker.exe"), new RunnerCommandValidator(), clock);
        var workspace = service.create(new WorkspaceRequest("11111111-1111-1111-1111-111111111111", "22222222-2222-2222-2222-222222222222", "STAGE-GIT-01", 0));
        clock.advance(Duration.ofMinutes(15).plusSeconds(1));

        service.cleanupExpiredWorkspaces();

        verify(docker).run(argThat(arguments -> arguments.getFirst().equals("rm")), any(Duration.class));
        assertThatThrownBy(() -> service.snapshotFor(workspace.workspaceId(), "11111111-1111-1111-1111-111111111111", 0)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsExpiredWorkspaceEvenWhenItsFirstCleanupAttemptFails() {
        DockerGateway docker = fixtureDocker();
        MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
        var service = new RunnerWorkspaceService(docker, new RunnerProperties("a".repeat(43), IMAGE, FINGERPRINT, "C:\\docker.exe"), new RunnerCommandValidator(), clock);
        var workspace = service.create(new WorkspaceRequest("11111111-1111-1111-1111-111111111111", "22222222-2222-2222-2222-222222222222", "STAGE-GIT-01", 0));
        when(docker.run(argThat(arguments -> arguments.getFirst().equals("rm")), any(Duration.class))).thenReturn(new DockerGateway.ProcessResult(1, "", "cleanup denied", false));
        clock.advance(Duration.ofMinutes(15));

        service.cleanupExpiredWorkspaces();

        assertThatThrownBy(() -> service.snapshotFor(workspace.workspaceId(), "11111111-1111-1111-1111-111111111111", 0)).isInstanceOf(IllegalArgumentException.class);
    }

    private DockerGateway fixtureDocker() {
        DockerGateway docker = mock(DockerGateway.class);
        when(docker.run(any(), any(Duration.class))).thenAnswer(invocation -> response(invocation.getArgument(0)));
        return docker;
    }

    private DockerGateway.ProcessResult response(List<String> arguments) {
        if (arguments.getFirst().equals("image")) return result(IMAGE + "|linux/amd64|{\"io.developer-dungeon.challenge.build-input-sha256\":\"" + FINGERPRINT + "\"}");
        if (arguments.getFirst().equals("rm")) return result("");
        if (arguments.getFirst().equals("run")) return result(CONTAINER + "\n");
        if (arguments.contains("/bin/cp") || arguments.contains("/bin/chmod")) return result("");
        if (arguments.getFirst().equals("container")) return result(IMAGE + "|{\"io.developer-dungeon.project\":\"developer-dungeon\",\"io.developer-dungeon.owner\":\"git-runner\"}");
        if (arguments.contains("config")) return result("core.repositoryformatversion=0\ncore.filemode=true\ncore.bare=false\ncore.logallrefupdates=true\n");
        if (arguments.contains("/usr/bin/find")) return result("");
        if (arguments.contains("/usr/bin/test")) return new DockerGateway.ProcessResult(arguments.contains("!") ? 0 : 1, "", "", false);
        if (arguments.contains("rev-parse") && arguments.contains("HEAD^{tree}")) return result("b".repeat(40) + "\n");
        if (arguments.contains("rev-parse")) return result("c".repeat(40) + "\n");
        if (arguments.contains("show")) return result("b".repeat(40) + "\n");
        if (arguments.contains("rev-list")) return result("c".repeat(40) + "\n" + "b".repeat(40) + "\n");
        if (arguments.contains("status")) return result("");
        throw new AssertionError("Unexpected Docker arguments: " + arguments);
    }

    private DockerGateway.ProcessResult result(String stdout) { return new DockerGateway.ProcessResult(0, stdout, "", false); }

    private static final class MutableClock extends Clock {
        private Instant instant;
        MutableClock(Instant instant) { this.instant = instant; }
        void advance(Duration duration) { instant = instant.plus(duration); }
        @Override public ZoneId getZone() { return ZoneId.of("UTC"); }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return instant; }
    }
}
