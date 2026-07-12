package jp.yuya.dev.developerdungeon.app;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import java.util.List;
import jp.yuya.dev.developerdungeon.contract.*;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class StageOneServiceTest {
    @Test void resetRetainsLogicalAttemptIdAndChangesGeneration() {
        RunnerClient runner = mock(RunnerClient.class);
        var snapshot = new RepositorySnapshot("c2", "bad-tree", "safe-tree", List.of("c1"), true, false, List.of("c2", "c1"));
        when(runner.create(any())).thenAnswer(invocation -> {
            WorkspaceRequest request = invocation.getArgument(0);
            return new WorkspaceResponse("workspace-" + request.generation(), request.generation(), snapshot);
        });
        var service = new StageOneService(runner, new GitCommandParser(), new StageOneGrader(), new OutputSanitizer());
        service.open(); service.reset();
        var requests = ArgumentCaptor.forClass(WorkspaceRequest.class); verify(runner, times(2)).create(requests.capture());
        assertThat(requests.getAllValues().get(1).attemptId()).isEqualTo(requests.getAllValues().getFirst().attemptId());
        assertThat(requests.getAllValues().get(1).generation()).isEqualTo(1);
    }
    @Test void duplicateRequestIdExecutesGitOnlyOnce() {
        RunnerClient runner = mock(RunnerClient.class);
        var initial = new RepositorySnapshot("c2", "bad-tree", "safe-tree", List.of("c1"), true, false, List.of("c2", "c1"));
        when(runner.create(any())).thenReturn(new WorkspaceResponse("workspace", 0, initial));
        when(runner.execute(any())).thenReturn(new CommandResponse(0, "", "", false, 1, initial));
        var service = new StageOneService(runner, new GitCommandParser(), new StageOneGrader(), new OutputSanitizer());
        service.open(); String requestId = "11111111-1111-1111-1111-111111111111";
        service.execute("git status", requestId); service.execute("git status", requestId);
        verify(runner, times(1)).execute(any());
    }
    @Test void resetAndRecoveryPreserveCommandSequenceAndSeparateCounters() {
        RunnerClient runner = mock(RunnerClient.class);
        var initial = new RepositorySnapshot("c2", "bad-tree", "safe-tree", List.of("c1"), true, false, List.of("c2", "c1"));
        when(runner.create(any())).thenAnswer(invocation -> {
            WorkspaceRequest request = invocation.getArgument(0);
            return new WorkspaceResponse("workspace-" + request.generation(), request.generation(), initial);
        });
        when(runner.execute(any())).thenThrow(new IllegalStateException("runner unavailable"));
        var service = new StageOneService(runner, new GitCommandParser(), new StageOneGrader(), new OutputSanitizer());

        service.open();
        var recovered = service.execute("git status", "11111111-1111-1111-1111-111111111111");
        var reset = service.reset();

        assertThat(recovered.systemRecoveryCount()).isEqualTo(1);
        assertThat(recovered.resetCount()).isZero();
        assertThat(recovered.commandSequence()).isEqualTo(1);
        assertThat(reset.systemRecoveryCount()).isEqualTo(1);
        assertThat(reset.resetCount()).isEqualTo(1);
        assertThat(reset.commandSequence()).isEqualTo(1);
    }
    @Test void destroysWorkspaceWhenTheStageIsCleared() {
        RunnerClient runner = mock(RunnerClient.class);
        var initial = new RepositorySnapshot("c2", "bad-tree", "safe-tree", List.of("c1"), true, false, List.of("c2", "c1"));
        var cleared = new RepositorySnapshot("c3", "safe-tree", "bad-tree", List.of("c2"), true, false, List.of("c3", "c2", "c1"));
        when(runner.create(any())).thenReturn(new WorkspaceResponse("workspace", 0, initial));
        when(runner.execute(any())).thenReturn(new CommandResponse(0, "", "", false, 1, cleared));
        var service = new StageOneService(runner, new GitCommandParser(), new StageOneGrader(), new OutputSanitizer());

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
            return new WorkspaceResponse("workspace-" + request.generation(), request.generation(), initial);
        });
        when(runner.execute(any())).thenReturn(new CommandResponse(0, "", "", false, 1, cleared));
        doThrow(new IllegalStateException("cleanup failed")).when(runner).destroy(any(DestroyRequest.class));
        var service = new StageOneService(runner, new GitCommandParser(), new StageOneGrader(), new OutputSanitizer());

        var view = service.execute("git status", "11111111-1111-1111-1111-111111111111");

        assertThat(view.cleared()).isFalse();
        assertThat(view.output()).contains("再接続後にもう一度試してください");
        verify(runner, times(1)).create(any());
    }
    @Test void resetDoesNotCreateNewWorkspaceWhenCleanupFails() {
        RunnerClient runner = mock(RunnerClient.class);
        var initial = new RepositorySnapshot("c2", "bad-tree", "safe-tree", List.of("c1"), true, false, List.of("c2", "c1"));
        when(runner.create(any())).thenReturn(new WorkspaceResponse("workspace", 0, initial));
        doThrow(new IllegalStateException("cleanup failed")).when(runner).destroy(any(DestroyRequest.class));
        var service = new StageOneService(runner, new GitCommandParser(), new StageOneGrader(), new OutputSanitizer());

        service.open();
        var view = service.reset();

        assertThat(view.output()).contains("新しい作業環境は作成しません");
        verify(runner, times(1)).create(any());
    }
}
