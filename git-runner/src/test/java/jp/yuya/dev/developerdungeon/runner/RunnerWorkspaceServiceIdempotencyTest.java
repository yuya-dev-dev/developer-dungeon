package jp.yuya.dev.developerdungeon.runner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import jp.yuya.dev.developerdungeon.contract.CommandKind;
import jp.yuya.dev.developerdungeon.contract.ExecuteRequest;
import jp.yuya.dev.developerdungeon.contract.GitCommand;
import jp.yuya.dev.developerdungeon.contract.DestroyRequest;
import jp.yuya.dev.developerdungeon.contract.WorkspaceRequest;

class RunnerWorkspaceServiceIdempotencyTest {
    private static final String IMAGE = "sha256:" + "a".repeat(64);
    private static final String FINGERPRINT = "b".repeat(64);
    private static final String CONTAINER = "c".repeat(64);

    @Test
    void duplicateCreateRequestUsesTheExistingWorkspace() {
        DockerGateway docker = fixtureDocker();
        RunnerWorkspaceService service = service(docker);
        var request = new WorkspaceRequest("11111111-1111-1111-1111-111111111111", "22222222-2222-2222-2222-222222222222", "STAGE-GIT-01", 0);

        var first = service.create(request);
        var second = service.create(request);

        assertThat(second).isSameAs(first);
        verify(docker, times(1)).run(argThat(arguments -> arguments.size() > 1 && arguments.getFirst().equals("run") && arguments.contains("-d")), any(Duration.class));
    }

    @Test
    void rejectsInvalidRequestIdentityBeforeCallingDocker() {
        DockerGateway docker = mock(DockerGateway.class);
        RunnerWorkspaceService service = service(docker);

        assertThatThrownBy(() -> service.create(new WorkspaceRequest("not-a-uuid", "22222222-2222-2222-2222-222222222222", "STAGE-GIT-01", 0)))
                .isInstanceOf(IllegalArgumentException.class);
        verify(docker, times(0)).run(any(), any(Duration.class));
    }

    @Test
    void removesContainerExactlyOnceWhenContainerIdentityVerificationFails() {
        DockerGateway docker = fixtureDocker();
        when(docker.run(argThat(arguments -> arguments.getFirst().equals("container")), any(Duration.class)))
                .thenReturn(result(IMAGE + "|{}"));
        RunnerWorkspaceService service = service(docker);

        assertThatThrownBy(() -> service.create(new WorkspaceRequest("11111111-1111-1111-1111-111111111111", "22222222-2222-2222-2222-222222222222", "STAGE-GIT-01", 0)))
                .isInstanceOf(IllegalStateException.class);

        verify(docker, times(1)).run(argThat(arguments -> arguments.getFirst().equals("rm")), any(Duration.class));
    }

    @Test
    void rejectsRevertOfAnAncestorOtherThanTheAccidentalCommit() {
        DockerGateway docker = fixtureDocker();
        RunnerWorkspaceService service = service(docker);
        var workspace = service.create(new WorkspaceRequest("11111111-1111-1111-1111-111111111111", "22222222-2222-2222-2222-222222222222", "STAGE-GIT-01", 0));
        clearInvocations(docker);

        assertThatThrownBy(() -> service.execute(new ExecuteRequest("11111111-1111-1111-1111-111111111111", "33333333-3333-3333-3333-333333333333", workspace.workspaceId(), 0,
                new GitCommand(CommandKind.REVERT_NO_EDIT, "b".repeat(40)))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("only the stage's accidental commit can be reverted");

        verify(docker, times(1)).run(argThat(arguments -> arguments.contains("cat-file")), any(Duration.class));
        verify(docker, times(0)).run(argThat(arguments -> arguments.contains("revert")), any(Duration.class));
    }

    @Test
    void rejectsObjectOutsideTheCapturedAllowlistBeforeInspectingOrExecutingIt() {
        DockerGateway docker = fixtureDocker();
        RunnerWorkspaceService service = service(docker);
        var workspace = service.create(new WorkspaceRequest("11111111-1111-1111-1111-111111111111", "22222222-2222-2222-2222-222222222222", "STAGE-GIT-01", 0));
        clearInvocations(docker);

        assertThatThrownBy(() -> service.execute(new ExecuteRequest("11111111-1111-1111-1111-111111111111", "33333333-3333-3333-3333-333333333333", workspace.workspaceId(), 0,
                new GitCommand(CommandKind.SHOW, "d".repeat(40)))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("object is not allowed for this stage");

        verify(docker, times(0)).run(argThat(arguments -> arguments.contains("cat-file")), any(Duration.class));
        verify(docker, times(0)).run(argThat(arguments -> arguments.contains("--no-ext-diff")), any(Duration.class));
    }

    @Test
    void rejectsAllowedNonCommitBeforeExecutingThePlayerCommand() {
        DockerGateway docker = fixtureDocker();
        RunnerWorkspaceService service = service(docker);
        var workspace = service.create(new WorkspaceRequest("11111111-1111-1111-1111-111111111111", "22222222-2222-2222-2222-222222222222", "STAGE-GIT-01", 0));
        clearInvocations(docker);
        when(docker.run(argThat(arguments -> arguments.contains("cat-file") && arguments.contains("-t")), any(Duration.class)))
                .thenReturn(result("blob\n"));

        assertThatThrownBy(() -> service.execute(new ExecuteRequest("11111111-1111-1111-1111-111111111111", "33333333-3333-3333-3333-333333333333", workspace.workspaceId(), 0,
                new GitCommand(CommandKind.SHOW, "b".repeat(40)))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("object is not a commit");

        verify(docker, times(1)).run(argThat(arguments -> arguments.contains("cat-file")), any(Duration.class));
        verify(docker, times(0)).run(argThat(arguments -> arguments.contains("--no-ext-diff")), any(Duration.class));
    }

    @Test
    void rejectsStageTwoCommandForStageOneBeforeExecutingGit() {
        DockerGateway docker = fixtureDocker();
        RunnerWorkspaceService service = service(docker);
        var workspace = service.create(new WorkspaceRequest("11111111-1111-1111-1111-111111111111", "22222222-2222-2222-2222-222222222222", "STAGE-GIT-01", 0));

        assertThatThrownBy(() -> service.execute(new ExecuteRequest("11111111-1111-1111-1111-111111111111", "33333333-3333-3333-3333-333333333333", workspace.workspaceId(), 0,
                new GitCommand(CommandKind.CHERRY_PICK, "c".repeat(40)))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("command is not allowed for this stage");

        verify(docker, times(0)).run(argThat(arguments -> arguments.contains("cherry-pick")), any(Duration.class));
    }

    @Test
    void failsClosedWhenGitStateFileCheckCannotBeCompleted() {
        DockerGateway docker = fixtureDocker();
        when(docker.run(argThat(arguments -> arguments.contains("/usr/bin/test") && arguments.stream().anyMatch(argument -> argument.contains("CHERRY_PICK_HEAD"))), any(Duration.class)))
                .thenReturn(new DockerGateway.ProcessResult(2, "", "permission denied", false));
        RunnerWorkspaceService service = service(docker);

        assertThatThrownBy(() -> service.create(new WorkspaceRequest("11111111-1111-1111-1111-111111111111", "22222222-2222-2222-2222-222222222222", "STAGE-GIT-01", 0)))
                .isInstanceOf(IllegalStateException.class).hasMessage("Git state file check failed");
    }

    @Test
    void failsClosedWhenSnapshotOutputIsTruncated() {
        DockerGateway docker = fixtureDocker();
        when(docker.run(argThat(arguments -> arguments.contains("rev-list")), any(Duration.class)))
                .thenReturn(new DockerGateway.ProcessResult(0, "c".repeat(40), "", true));
        RunnerWorkspaceService service = service(docker);

        assertThatThrownBy(() -> service.create(new WorkspaceRequest("11111111-1111-1111-1111-111111111111", "22222222-2222-2222-2222-222222222222", "STAGE-GIT-01", 0)))
                .isInstanceOf(IllegalStateException.class).hasMessage("snapshot failed");
    }

    @Test
    void runsGitWithFixedConfigurationAndNoExternalDiffOrTextConversion() {
        DockerGateway docker = fixtureDocker();
        RunnerWorkspaceService service = service(docker);
        var workspace = service.create(new WorkspaceRequest("11111111-1111-1111-1111-111111111111", "22222222-2222-2222-2222-222222222222", "STAGE-GIT-01", 0));

        service.execute(new ExecuteRequest("11111111-1111-1111-1111-111111111111", "33333333-3333-3333-3333-333333333333", workspace.workspaceId(), 0,
                new GitCommand(CommandKind.SHOW, "c".repeat(40))));

        verify(docker).run(argThat(arguments -> arguments.containsAll(List.of("GIT_CONFIG_NOSYSTEM=1", "GIT_CONFIG_GLOBAL=/dev/null", "GIT_TERMINAL_PROMPT=0", "GIT_ALLOW_PROTOCOL=", "GIT_PROTOCOL_FROM_USER=0", "--no-ext-diff", "--no-textconv"))), any(Duration.class));
    }

    @Test
    void duplicateExecuteRequestRunsGitOnlyOnce() {
        DockerGateway docker = fixtureDocker();
        RunnerWorkspaceService service = service(docker);
        var workspace = service.create(new WorkspaceRequest("11111111-1111-1111-1111-111111111111", "22222222-2222-2222-2222-222222222222", "STAGE-GIT-01", 0));
        var request = new ExecuteRequest("11111111-1111-1111-1111-111111111111", "33333333-3333-3333-3333-333333333333", workspace.workspaceId(), 0,
                new GitCommand(CommandKind.SHOW, "c".repeat(40)));
        clearInvocations(docker);

        service.execute(request);
        service.execute(request);

        verify(docker, times(1)).run(argThat(arguments -> arguments.contains("cat-file")), any(Duration.class));
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

    @Test
    void createAddsIdentityAndFingerprintLabelsToTheContainer() {
        DockerGateway docker = fixtureDocker();
        RunnerWorkspaceService service = service(docker);

        service.create(new WorkspaceRequest("11111111-1111-1111-1111-111111111111", "22222222-2222-2222-2222-222222222222", "STAGE-GIT-01", 0));

        verify(docker).run(argThat(arguments -> arguments.getFirst().equals("run")
                && arguments.contains("io.developer-dungeon.attempt=11111111-1111-1111-1111-111111111111")
                && arguments.contains("io.developer-dungeon.challenge.build-input-sha256=" + FINGERPRINT)), any(Duration.class));
    }

    @Test
    void cleanupFailurePreventsNewContainerCreationUntilDeletionSucceeds() {
        DockerGateway docker = fixtureDocker();
        RunnerWorkspaceService service = service(docker);
        var workspace = service.create(new WorkspaceRequest("11111111-1111-1111-1111-111111111111", "22222222-2222-2222-2222-222222222222", "STAGE-GIT-01", 0));
        when(docker.run(argThat(arguments -> arguments.getFirst().equals("rm")), any(Duration.class))).thenReturn(new DockerGateway.ProcessResult(1, "", "cleanup denied", false));

        assertThatThrownBy(() -> service.destroy(new DestroyRequest("11111111-1111-1111-1111-111111111111", "44444444-4444-4444-4444-444444444444", workspace.workspaceId(), 0, "player-reset")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("container cleanup failed");
        assertThatThrownBy(() -> service.create(new WorkspaceRequest("11111111-1111-1111-1111-111111111111", "55555555-5555-5555-5555-555555555555", "STAGE-GIT-01", 1)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("challenge cleanup is incomplete");
    }

    @Test
    void uncertainContainerCreationDegradesRunnerAndBlocksLaterCreateBeforeDocker() {
        DockerGateway docker = mock(DockerGateway.class);
        when(docker.run(any(), any(Duration.class))).thenAnswer(invocation -> {
            List<String> arguments = invocation.getArgument(0);
            if (arguments.getFirst().equals("image")) return result(IMAGE + "|linux/amd64|{\"io.developer-dungeon.challenge.build-input-sha256\":\"" + FINGERPRINT + "\"}");
            throw new IllegalStateException("uncertain Docker result");
        });
        RunnerWorkspaceService service = service(docker);

        assertThatThrownBy(() -> service.create(new WorkspaceRequest("11111111-1111-1111-1111-111111111111", "22222222-2222-2222-2222-222222222222", "STAGE-GIT-01", 0)))
                .isInstanceOf(IllegalStateException.class);
        int callsAfterFailure = org.mockito.Mockito.mockingDetails(docker).getInvocations().size();
        assertThat(service.isReady()).isFalse();
        assertThatThrownBy(() -> service.create(new WorkspaceRequest("11111111-1111-1111-1111-111111111111", "33333333-3333-3333-3333-333333333333", "STAGE-GIT-01", 1)))
                .hasMessage("Git Runner requires cleanup recovery");
        assertThat(org.mockito.Mockito.mockingDetails(docker).getInvocations()).hasSize(callsAfterFailure);
    }

    @Test
    void attachFailureRemovesKnownContainerBeforeRunnerAcceptsAnotherCreate() {
        DockerGateway docker = fixtureDocker();
        ContainerOwnershipLedger ledger = mock(ContainerOwnershipLedger.class);
        doThrow(new IllegalStateException("ledger write failed")).doNothing().when(ledger).attachContainer(any(String.class), any(String.class));
        var service = new RunnerWorkspaceService(docker, new RunnerProperties("a".repeat(43), IMAGE, FINGERPRINT, "C:\\docker.exe"),
                new RunnerCommandValidator(), Clock.systemUTC(), ledger);

        assertThatThrownBy(() -> service.create(new WorkspaceRequest("11111111-1111-1111-1111-111111111111", "22222222-2222-2222-2222-222222222222", "STAGE-GIT-01", 0)))
                .isInstanceOf(IllegalStateException.class);
        assertThat(service.isReady()).isTrue();
        service.create(new WorkspaceRequest("11111111-1111-1111-1111-111111111111", "33333333-3333-3333-3333-333333333333", "STAGE-GIT-01", 1));
        verify(docker, times(1)).run(argThat(arguments -> arguments.getFirst().equals("rm")), any(Duration.class));
        verify(ledger).remove(any(String.class));
    }

    @Test
    void acceptsAppDestroyAfterTtlAlreadyDeletedWorkspaceWithoutCallingDockerAgain() {
        DockerGateway docker = fixtureDocker();
        MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
        ContainerOwnershipLedger ledger = new MemoryContainerOwnershipLedger(clock);
        var service = new RunnerWorkspaceService(docker, new RunnerProperties("a".repeat(43), IMAGE, FINGERPRINT, "C:\\docker.exe"),
                new RunnerCommandValidator(), clock, ledger);
        var workspace = service.create(new WorkspaceRequest("11111111-1111-1111-1111-111111111111", "22222222-2222-2222-2222-222222222222", "STAGE-GIT-01", 0));
        clock.advance(Duration.ofMinutes(15).plusSeconds(1));
        service.cleanupExpiredWorkspaces();

        service.destroy(new DestroyRequest("11111111-1111-1111-1111-111111111111", "44444444-4444-4444-4444-444444444444", workspace.workspaceId(), 0, "system-recovery"));

        verify(docker, times(1)).run(argThat(arguments -> arguments.getFirst().equals("rm")), any(Duration.class));
    }

    @Test
    void startupRecoveryKeepsDeletedTombstoneForAppRecoveryWithoutCallingDockerAgain() {
        DockerGateway docker = mock(DockerGateway.class);
        when(docker.run(any(), any(Duration.class))).thenAnswer(invocation -> {
            List<String> arguments = invocation.getArgument(0);
            if (arguments.getFirst().equals("container")) return result(ownedContainerInspection("11111111-1111-1111-1111-111111111111", "22222222-2222-2222-2222-222222222222"));
            if (arguments.getFirst().equals("rm")) return result("");
            throw new AssertionError("Unexpected Docker arguments: " + arguments);
        });
        ContainerOwnershipLedger ledger = new MemoryContainerOwnershipLedger(Clock.systemUTC());
        ledger.recordIntent("11111111-1111-1111-1111-111111111111", "22222222-2222-2222-2222-222222222222", 0, IMAGE, FINGERPRINT);
        ledger.attachContainer("22222222-2222-2222-2222-222222222222", CONTAINER);
        var service = new RunnerWorkspaceService(docker, new RunnerProperties("a".repeat(43), IMAGE, FINGERPRINT, "C:\\docker.exe"),
                new RunnerCommandValidator(), Clock.systemUTC(), ledger);

        service.cleanupOrphansOnStartup();

        assertThat(service.isReady()).isTrue();
        assertThat(ledger.entries()).singleElement().satisfies(entry -> {
            assertThat(entry.state()).isEqualTo(ContainerOwnershipLedger.State.DELETED);
            assertThat(entry.attemptId()).isEqualTo("11111111-1111-1111-1111-111111111111");
            assertThat(entry.workspaceId()).isEqualTo("22222222-2222-2222-2222-222222222222");
            assertThat(entry.generation()).isZero();
        });
        service.destroy(new DestroyRequest("11111111-1111-1111-1111-111111111111", "33333333-3333-3333-3333-333333333333",
                "22222222-2222-2222-2222-222222222222", 0, "startup-system-recovery"));
        verify(docker).run(argThat(arguments -> arguments.getFirst().equals("rm") && arguments.contains(CONTAINER)), any(Duration.class));
        verify(docker, times(1)).run(argThat(arguments -> arguments.getFirst().equals("rm")), any(Duration.class));
    }

    @Test
    void periodicRecoveryKeepsDeletedTombstoneForAppRecoveryWithoutCallingDockerAgain() {
        DockerGateway docker = mock(DockerGateway.class);
        when(docker.run(any(), any(Duration.class))).thenAnswer(invocation -> {
            List<String> arguments = invocation.getArgument(0);
            if (arguments.getFirst().equals("container")) return result(ownedContainerInspection("11111111-1111-1111-1111-111111111111", "22222222-2222-2222-2222-222222222222"));
            if (arguments.getFirst().equals("rm")) return result("");
            throw new AssertionError("Unexpected Docker arguments: " + arguments);
        });
        MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:16:00Z"));
        ContainerOwnershipLedger ledger = new MemoryContainerOwnershipLedger(Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneId.of("UTC")));
        ledger.recordIntent("11111111-1111-1111-1111-111111111111", "22222222-2222-2222-2222-222222222222", 0, IMAGE, FINGERPRINT);
        ledger.attachContainer("22222222-2222-2222-2222-222222222222", CONTAINER);
        var service = new RunnerWorkspaceService(docker, new RunnerProperties("a".repeat(43), IMAGE, FINGERPRINT, "C:\\docker.exe"),
                new RunnerCommandValidator(), clock, ledger);

        service.cleanupExpiredWorkspaces();
        service.destroy(new DestroyRequest("11111111-1111-1111-1111-111111111111", "33333333-3333-3333-3333-333333333333",
                "22222222-2222-2222-2222-222222222222", 0, "startup-system-recovery"));

        assertThat(ledger.entries()).singleElement().extracting(ContainerOwnershipLedger.Entry::state)
                .isEqualTo(ContainerOwnershipLedger.State.DELETED);
        verify(docker, times(1)).run(argThat(arguments -> arguments.getFirst().equals("rm")), any(Duration.class));
    }

    @Test
    void rejectsUnknownWorkspaceThatHasNoMatchingDeletedTombstone() {
        DockerGateway docker = mock(DockerGateway.class);
        var service = new RunnerWorkspaceService(docker, new RunnerProperties("a".repeat(43), IMAGE, FINGERPRINT, "C:\\docker.exe"),
                new RunnerCommandValidator());

        assertThatThrownBy(() -> service.destroy(new DestroyRequest("11111111-1111-1111-1111-111111111111",
                "33333333-3333-3333-3333-333333333333", "22222222-2222-2222-2222-222222222222", 0, "startup-system-recovery")))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("unknown workspace");

        verify(docker, times(0)).run(any(), any(Duration.class));
    }

    @Test
    void rejectsDeletedTombstoneWhenAttemptOrGenerationDoesNotMatch() {
        DockerGateway docker = mock(DockerGateway.class);
        ContainerOwnershipLedger ledger = new MemoryContainerOwnershipLedger(Clock.systemUTC());
        ledger.recordIntent("11111111-1111-1111-1111-111111111111", "22222222-2222-2222-2222-222222222222", 0, IMAGE, FINGERPRINT);
        ledger.attachContainer("22222222-2222-2222-2222-222222222222", CONTAINER);
        ledger.markDeleted("22222222-2222-2222-2222-222222222222", "33333333-3333-3333-3333-333333333333");
        var service = new RunnerWorkspaceService(docker, new RunnerProperties("a".repeat(43), IMAGE, FINGERPRINT, "C:\\docker.exe"),
                new RunnerCommandValidator(), Clock.systemUTC(), ledger);

        assertThatThrownBy(() -> service.destroy(new DestroyRequest("44444444-4444-4444-4444-444444444444",
                "55555555-5555-5555-5555-555555555555", "22222222-2222-2222-2222-222222222222", 0, "startup-system-recovery")))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("unknown workspace");
        assertThatThrownBy(() -> service.destroy(new DestroyRequest("11111111-1111-1111-1111-111111111111",
                "66666666-6666-6666-6666-666666666666", "22222222-2222-2222-2222-222222222222", 1, "startup-system-recovery")))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("unknown workspace");

        verify(docker, times(0)).run(any(), any(Duration.class));
    }

    @Test
    void startupIdentityMismatchDegradesRunnerWithoutRemovingContainer() {
        DockerGateway docker = mock(DockerGateway.class);
        when(docker.run(any(), any(Duration.class))).thenReturn(result(CONTAINER + "|" + IMAGE + "|{}"));
        ContainerOwnershipLedger ledger = new MemoryContainerOwnershipLedger(Clock.systemUTC());
        ledger.recordIntent("11111111-1111-1111-1111-111111111111", "22222222-2222-2222-2222-222222222222", 0, IMAGE, FINGERPRINT);
        ledger.attachContainer("22222222-2222-2222-2222-222222222222", CONTAINER);
        var service = new RunnerWorkspaceService(docker, new RunnerProperties("a".repeat(43), IMAGE, FINGERPRINT, "C:\\docker.exe"),
                new RunnerCommandValidator(), Clock.systemUTC(), ledger);

        service.cleanupOrphansOnStartup();

        assertThat(service.isReady()).isFalse();
        verify(docker, times(0)).run(argThat(arguments -> arguments.getFirst().equals("rm")), any(Duration.class));
    }

    @Test
    void shutdownMakesRunnerUnreadyAndRejectsCreateBeforeDocker() {
        DockerGateway docker = mock(DockerGateway.class);
        RunnerWorkspaceService service = service(docker);

        service.beginShutdown();

        assertThat(service.isReady()).isFalse();
        assertThatThrownBy(() -> service.create(new WorkspaceRequest("11111111-1111-1111-1111-111111111111", "22222222-2222-2222-2222-222222222222", "STAGE-GIT-01", 0)))
                .hasMessage("Git Runner is shutting down");
        verify(docker, times(0)).run(any(), any(Duration.class));
    }

    private RunnerWorkspaceService service(DockerGateway docker) {
        RunnerProperties properties = new RunnerProperties("a".repeat(43), IMAGE, FINGERPRINT,
                "C:\\docker.exe");
        return new RunnerWorkspaceService(docker, properties, new RunnerCommandValidator());
    }

    private String ownedContainerInspection(String attemptId, String workspaceId) {
        return CONTAINER + "|" + IMAGE + "|{\"io.developer-dungeon.project\":\"developer-dungeon\",\"io.developer-dungeon.owner\":\"git-runner\","
                + "\"io.developer-dungeon.attempt\":\"" + attemptId + "\",\"io.developer-dungeon.workspace\":\"" + workspaceId + "\","
                + "\"io.developer-dungeon.challenge.build-input-sha256\":\"" + FINGERPRINT + "\"}";
    }

    private DockerGateway fixtureDocker() {
        DockerGateway docker = mock(DockerGateway.class);
        Map<String, String> labels = new HashMap<>();
        when(docker.run(any(), any(Duration.class))).thenAnswer(invocation -> response(invocation.getArgument(0), labels));
        return docker;
    }

    private DockerGateway.ProcessResult response(List<String> arguments, Map<String, String> labels) {
        if (arguments.getFirst().equals("image")) return result(IMAGE + "|linux/amd64|{\"io.developer-dungeon.challenge.build-input-sha256\":\"" + FINGERPRINT + "\"}");
        if (arguments.getFirst().equals("rm")) return result("");
        if (arguments.getFirst().equals("run")) {
            for (int index = 0; index < arguments.size() - 1; index++) {
                if ("--label".equals(arguments.get(index))) {
                    String[] parts = arguments.get(index + 1).split("=", 2);
                    if (parts.length == 2) labels.put(parts[0], parts[1]);
                }
            }
            return result(CONTAINER + "\n");
        }
        if (arguments.contains("/bin/cp") || arguments.contains("/bin/chmod")) return result("");
        if (arguments.getFirst().equals("container")) {
            String identity = arguments.stream().anyMatch(argument -> argument.contains("{{.Id}}")) ? CONTAINER + "|" + IMAGE : IMAGE;
            return result(identity + "|{\"io.developer-dungeon.project\":\"" + labels.get("io.developer-dungeon.project")
                + "\",\"io.developer-dungeon.owner\":\"" + labels.get("io.developer-dungeon.owner")
                + "\",\"io.developer-dungeon.attempt\":\"" + labels.get("io.developer-dungeon.attempt")
                + "\",\"io.developer-dungeon.workspace\":\"" + labels.get("io.developer-dungeon.workspace")
                + "\",\"io.developer-dungeon.challenge.build-input-sha256\":\"" + labels.get("io.developer-dungeon.challenge.build-input-sha256") + "\"}");
        }
        if (arguments.contains("config")) return result("core.repositoryformatversion=0\ncore.filemode=true\ncore.bare=false\ncore.logallrefupdates=true\n");
        if (arguments.contains("/usr/bin/find")) return result("");
        if (arguments.contains("/usr/bin/test")) return new DockerGateway.ProcessResult(arguments.contains("!") ? 0 : 1, "", "", false);
        if (arguments.contains("rev-parse") && arguments.contains("HEAD^{tree}")) return result("b".repeat(40) + "\n");
        if (arguments.contains("rev-parse")) return result("c".repeat(40) + "\n");
        if (arguments.contains("cat-file") && arguments.contains("-t")) return result("commit\n");
        if (arguments.contains("show")) return result("b".repeat(40) + "\n");
        if (arguments.contains("rev-list")) return result("c".repeat(40) + "\n" + "b".repeat(40) + "\n");
        if (arguments.contains("branch") && arguments.contains("--show-current")) return result("main\n");
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
