package jp.yuya.dev.developerdungeon.app;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.time.Clock;
import jp.yuya.dev.developerdungeon.contract.*;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class StageOneServiceTest {
    @Test void progressReadsPersistedStarsWithoutStartingAWorkspace() {
        RunnerClient runner = mock(RunnerClient.class);
        StagePersistence persistence = mock(StagePersistence.class);
        when(persistence.highestStars("STAGE-GIT-01")).thenReturn(3);
        var service = new StageService(runner, new GitCommandParser(), new StageOneGrader(), new OutputSanitizer(), persistence, Clock.systemUTC());

        var progress = service.progress();

        assertThat(progress.stageKey()).isEqualTo("STAGE-GIT-01");
        assertThat(progress.isCleared()).isTrue();
        assertThat(progress.highestStars()).isEqualTo(3);
        verify(persistence).highestStars("STAGE-GIT-01");
        verifyNoMoreInteractions(persistence);
        verifyNoInteractions(runner);
    }
    @Test void progressFailureDoesNotStartAWorkspace() {
        RunnerClient runner = mock(RunnerClient.class);
        StagePersistence persistence = mock(StagePersistence.class);
        when(persistence.highestStars("STAGE-GIT-01")).thenThrow(new IllegalStateException("database unavailable"));
        var service = new StageService(runner, new GitCommandParser(), new StageOneGrader(), new OutputSanitizer(), persistence, Clock.systemUTC());

        assertThatThrownBy(service::progress).isInstanceOf(IllegalStateException.class);

        verify(persistence).highestStars("STAGE-GIT-01");
        verifyNoMoreInteractions(persistence);
        verifyNoInteractions(runner);
    }
    @Test void resetRetainsLogicalAttemptIdAndChangesGeneration() {
        RunnerClient runner = mock(RunnerClient.class);
        var snapshot = new RepositorySnapshot("c2", "bad-tree", "safe-tree", List.of("c1"), true, false, List.of("c2", "c1"));
        when(runner.create(any())).thenAnswer(invocation -> {
            WorkspaceRequest request = invocation.getArgument(0);
            return new WorkspaceResponse(workspaceId(request), request.generation(), snapshot);
        });
        var service = new StageService(runner, new GitCommandParser(), new StageOneGrader(), new OutputSanitizer());
        service.open(); service.reset();
        var requests = ArgumentCaptor.forClass(WorkspaceRequest.class); verify(runner, times(2)).create(requests.capture());
        assertThat(requests.getAllValues().get(1).attemptId()).isEqualTo(requests.getAllValues().getFirst().attemptId());
        assertThat(requests.getAllValues().get(1).generation()).isEqualTo(1);
    }
    @Test void duplicateRequestIdExecutesGitOnlyOnce() {
        RunnerClient runner = mock(RunnerClient.class);
        var initial = new RepositorySnapshot("c2", "bad-tree", "safe-tree", List.of("c1"), true, false, List.of("c2", "c1"));
        when(runner.create(any())).thenAnswer(invocation -> { WorkspaceRequest request = invocation.getArgument(0); return new WorkspaceResponse(workspaceId(request), request.generation(), initial); });
        when(runner.execute(any())).thenReturn(new CommandResponse(0, "", "", false, 1, initial));
        var service = new StageService(runner, new GitCommandParser(), new StageOneGrader(), new OutputSanitizer());
        service.open(); String requestId = "11111111-1111-1111-1111-111111111111";
        service.execute("git status", requestId); service.execute("git status", requestId);
        verify(runner, times(1)).execute(any());
    }
    @Test void resetAndRecoveryPreserveCommandSequenceAndSeparateCounters() {
        RunnerClient runner = mock(RunnerClient.class);
        var initial = new RepositorySnapshot("c2", "bad-tree", "safe-tree", List.of("c1"), true, false, List.of("c2", "c1"));
        when(runner.create(any())).thenAnswer(invocation -> {
            WorkspaceRequest request = invocation.getArgument(0);
            return new WorkspaceResponse(workspaceId(request), request.generation(), initial);
        });
        when(runner.execute(any())).thenThrow(new IllegalStateException("runner unavailable"));
        var service = new StageService(runner, new GitCommandParser(), new StageOneGrader(), new OutputSanitizer());

        service.open();
        var recovered = service.execute("git status", "11111111-1111-1111-1111-111111111111");
        var reset = service.reset();

        assertThat(recovered.systemRecoveryCount()).isEqualTo(1);
        assertThat(recovered.resetCount()).isZero();
        assertThat(recovered.commandSequence()).isEqualTo(2);
        assertThat(reset.systemRecoveryCount()).isEqualTo(1);
        assertThat(reset.resetCount()).isEqualTo(1);
        assertThat(reset.commandSequence()).isEqualTo(3);
    }
    @Test void destroysWorkspaceWhenTheStageIsCleared() {
        RunnerClient runner = mock(RunnerClient.class);
        var initial = new RepositorySnapshot("c2", "bad-tree", "safe-tree", List.of("c1"), true, false, List.of("c2", "c1"));
        var cleared = new RepositorySnapshot("c3", "safe-tree", "bad-tree", List.of("c2"), true, false, List.of("c3", "c2", "c1"));
        when(runner.create(any())).thenAnswer(invocation -> { WorkspaceRequest request = invocation.getArgument(0); return new WorkspaceResponse(workspaceId(request), request.generation(), initial); });
        when(runner.execute(any())).thenReturn(new CommandResponse(0, "", "", false, 1, cleared));
        var service = new StageService(runner, new GitCommandParser(), new StageOneGrader(), new OutputSanitizer());

        var view = service.execute("git status", "11111111-1111-1111-1111-111111111111");
        var repeated = service.execute("git status", "11111111-1111-1111-1111-111111111111");

        assertThat(view.cleared()).isTrue();
        assertThat(repeated).isSameAs(view);
        verify(runner).destroy(any(DestroyRequest.class));
        verify(runner, times(1)).execute(any());
    }
    @Test void doesNotReportClearWhenWorkspaceCleanupFails() {
        RunnerClient runner = mock(RunnerClient.class);
        var initial = new RepositorySnapshot("c2", "bad-tree", "safe-tree", List.of("c1"), true, false, List.of("c2", "c1"));
        var cleared = new RepositorySnapshot("c3", "safe-tree", "bad-tree", List.of("c2"), true, false, List.of("c3", "c2", "c1"));
        when(runner.create(any())).thenAnswer(invocation -> {
            WorkspaceRequest request = invocation.getArgument(0);
            return new WorkspaceResponse(workspaceId(request), request.generation(), initial);
        });
        when(runner.execute(any())).thenReturn(new CommandResponse(0, "", "", false, 1, cleared));
        doThrow(new IllegalStateException("cleanup failed")).when(runner).destroy(any(DestroyRequest.class));
        var service = new StageService(runner, new GitCommandParser(), new StageOneGrader(), new OutputSanitizer());

        var view = service.execute("git status", "11111111-1111-1111-1111-111111111111");

        assertThat(view.cleared()).isFalse();
        assertThat(view.output()).contains("再接続後にもう一度試してください");
        verify(runner, times(1)).create(any());
    }
    @Test void resetDoesNotCreateNewWorkspaceWhenCleanupFails() {
        RunnerClient runner = mock(RunnerClient.class);
        var initial = new RepositorySnapshot("c2", "bad-tree", "safe-tree", List.of("c1"), true, false, List.of("c2", "c1"));
        when(runner.create(any())).thenAnswer(invocation -> { WorkspaceRequest request = invocation.getArgument(0); return new WorkspaceResponse(workspaceId(request), request.generation(), initial); });
        doThrow(new IllegalStateException("cleanup failed")).when(runner).destroy(any(DestroyRequest.class));
        var service = new StageService(runner, new GitCommandParser(), new StageOneGrader(), new OutputSanitizer());

        service.open();
        var view = service.reset();

        assertThat(view.output()).contains("新しい作業環境は作成しません");
        verify(runner, times(1)).create(any());
    }
    @Test void destroysCreatedWorkspaceWhenPersistenceActivationFails() {
        RunnerClient runner = mock(RunnerClient.class);
        StagePersistence persistence = mock(StagePersistence.class);
        UUID attemptId = UUID.randomUUID(); UUID createId = UUID.randomUUID(); UUID workspaceId = UUID.randomUUID();
        var starting = new StagePersistence.SavedAttempt(attemptId, "STARTING", 0, 0, null, createId, createId, null, 0, 0, 0, 0, null);
        var created = new StagePersistence.SavedAttempt(attemptId, "STARTING", 1, 0, workspaceId, createId, createId, null, 0, 0, 0, 0, null);
        var snapshot = new RepositorySnapshot("c2", "bad-tree", "safe-tree", List.of("c1"), true, false, List.of("c2", "c1"));
        when(persistence.findOpen("STAGE-GIT-01")).thenReturn(Optional.empty());
        when(persistence.createStarting(any(), any(), any(), any())).thenReturn(starting);
        when(runner.create(any())).thenReturn(new WorkspaceResponse(workspaceId.toString(), 0, snapshot));
        when(persistence.workspaceCreated(attemptId, 0, workspaceId)).thenReturn(created);
        doThrow(new IllegalStateException("database unavailable")).when(persistence).activate(attemptId, 1, workspaceId);
        when(persistence.beginCreateCleanup(eq(attemptId), eq(1L), eq(workspaceId))).thenReturn(new StagePersistence.SavedAttempt(attemptId, "CLEANUP_PENDING", 2, 0, workspaceId, createId, createId, null, 0, 0, 0, 0, null));
        when(persistence.restartStartingAfterCleanup(attemptId, 2)).thenReturn(starting);
        var service = new StageService(runner, new GitCommandParser(), new StageOneGrader(), new OutputSanitizer(), persistence, Clock.systemUTC());

        assertThatThrownBy(service::open).isInstanceOf(IllegalStateException.class);

        verify(runner).destroy(argThat(request -> request.attemptId().equals(attemptId.toString()) && request.workspaceId().equals(workspaceId.toString()) && request.requestId().equals(createId.toString())));
    }
    @Test void destroysCreatedWorkspaceWhenWorkspacePersistenceFailsBeforeCleanupStateIsSaved() {
        RunnerClient runner = mock(RunnerClient.class);
        StagePersistence persistence = mock(StagePersistence.class);
        UUID attemptId = UUID.randomUUID(); UUID createId = UUID.randomUUID(); UUID workspaceId = UUID.randomUUID();
        var starting = new StagePersistence.SavedAttempt(attemptId, "STARTING", 0, 0, null, createId, createId, null, 0, 0, 0, 0, null);
        var snapshot = new RepositorySnapshot("c2", "bad-tree", "safe-tree", List.of("c1"), true, false, List.of("c2", "c1"));
        when(persistence.findOpen("STAGE-GIT-01")).thenReturn(Optional.empty());
        when(persistence.createStarting(any(), any(), any(), any())).thenReturn(starting);
        when(runner.create(any())).thenReturn(new WorkspaceResponse(workspaceId.toString(), 0, snapshot));
        doThrow(new IllegalStateException("database unavailable")).when(persistence).workspaceCreated(attemptId, 0, workspaceId);
        var service = new StageService(runner, new GitCommandParser(), new StageOneGrader(), new OutputSanitizer(), persistence, Clock.systemUTC());

        assertThatThrownBy(service::open).isInstanceOf(IllegalStateException.class);

        verify(runner).destroy(argThat(request -> request.workspaceId().equals(workspaceId.toString()) && request.requestId().equals(createId.toString())));
    }
    @Test void destroysCreatedWorkspaceWhenCleanupStatePersistenceFails() {
        RunnerClient runner = mock(RunnerClient.class);
        StagePersistence persistence = mock(StagePersistence.class);
        UUID attemptId = UUID.randomUUID(); UUID createId = UUID.randomUUID(); UUID workspaceId = UUID.randomUUID();
        var starting = new StagePersistence.SavedAttempt(attemptId, "STARTING", 0, 0, null, createId, createId, null, 0, 0, 0, 0, null);
        var created = new StagePersistence.SavedAttempt(attemptId, "STARTING", 1, 0, workspaceId, createId, createId, null, 0, 0, 0, 0, null);
        var snapshot = new RepositorySnapshot("c2", "bad-tree", "safe-tree", List.of("c1"), true, false, List.of("c2", "c1"));
        when(persistence.findOpen("STAGE-GIT-01")).thenReturn(Optional.empty());
        when(persistence.createStarting(any(), any(), any(), any())).thenReturn(starting);
        when(runner.create(any())).thenReturn(new WorkspaceResponse(workspaceId.toString(), 0, snapshot));
        when(persistence.workspaceCreated(attemptId, 0, workspaceId)).thenReturn(created);
        doThrow(new IllegalStateException("database unavailable")).when(persistence).activate(attemptId, 1, workspaceId);
        doThrow(new IllegalStateException("cleanup state unavailable")).when(persistence).beginCreateCleanup(attemptId, 1, workspaceId);
        var service = new StageService(runner, new GitCommandParser(), new StageOneGrader(), new OutputSanitizer(), persistence, Clock.systemUTC());

        assertThatThrownBy(service::open).isInstanceOf(IllegalStateException.class);

        verify(runner).destroy(argThat(request -> request.workspaceId().equals(workspaceId.toString()) && request.requestId().equals(createId.toString())));
    }
    private static String workspaceId(WorkspaceRequest request) { return String.format("00000000-0000-0000-0000-%012d", request.generation() + 1); }
}
