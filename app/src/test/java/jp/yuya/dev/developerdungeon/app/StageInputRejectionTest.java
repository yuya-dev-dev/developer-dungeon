package jp.yuya.dev.developerdungeon.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.AdditionalAnswers.delegatesTo;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import jp.yuya.dev.developerdungeon.contract.CommandResponse;
import jp.yuya.dev.developerdungeon.contract.RepositorySnapshot;
import jp.yuya.dev.developerdungeon.contract.WorkspaceRequest;
import jp.yuya.dev.developerdungeon.contract.WorkspaceResponse;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class StageInputRejectionTest {
    @Test void persistsExplicitReasonCodesForSyntaxAndObjectRejections() {
        RunnerClient runner = mock(RunnerClient.class);
        when(runner.create(any())).thenAnswer(invocation -> workspace(invocation.getArgument(0)));
        StagePersistence persistence = mock(StagePersistence.class, delegatesTo(new MemoryStagePersistence()));
        StageService service = new StageService(runner, new StageRules(), new OutputSanitizer(), persistence, Clock.systemUTC());

        service.open("STAGE-GIT-01");
        assertThat(service.execute("STAGE-GIT-01", "git stash push", id(1)).feedbackKind()).isEqualTo(StageFeedbackKind.INPUT_REJECTED);
        assertThat(service.execute("STAGE-GIT-01", "git status; git log", id(2)).feedbackKind()).isEqualTo(StageFeedbackKind.INPUT_REJECTED);
        assertThat(service.execute("STAGE-GIT-01", "git show " + "c".repeat(12), id(3)).feedbackKind()).isEqualTo(StageFeedbackKind.INPUT_REJECTED);

        ArgumentCaptor<String> reasons = ArgumentCaptor.forClass(String.class);
        verify(persistence, times(3)).recordRejected(any(UUID.class), anyLong(), any(UUID.class), anyLong(), anyLong(), reasons.capture(), any(Instant.class));
        assertThat(reasons.getAllValues()).containsExactly("INVALID_SYNTAX", "INVALID_SYNTAX", "OBJECT_NOT_ALLOWED");
        verify(runner, times(0)).execute(any());
    }

    @Test void classifiesGitAndSystemFailuresSeparatelyFromInputRejections() {
        RunnerClient gitErrorRunner = mock(RunnerClient.class);
        when(gitErrorRunner.create(any())).thenAnswer(invocation -> workspace(invocation.getArgument(0)));
        when(gitErrorRunner.execute(any())).thenReturn(new CommandResponse(1, "", "fatal: bad revision", false, 1, initial()));
        StageService gitErrorService = new StageService(gitErrorRunner, new StageRules(), new OutputSanitizer());
        assertThat(gitErrorService.execute("STAGE-GIT-01", "git status", id(10)).feedbackKind()).isEqualTo(StageFeedbackKind.GIT_ERROR);

        RunnerClient systemErrorRunner = mock(RunnerClient.class);
        when(systemErrorRunner.create(any())).thenAnswer(invocation -> workspace(invocation.getArgument(0)));
        when(systemErrorRunner.execute(any())).thenThrow(new IllegalStateException("runner unavailable"));
        StageService systemErrorService = new StageService(systemErrorRunner, new StageRules(), new OutputSanitizer());
        assertThat(systemErrorService.execute("STAGE-GIT-01", "git status", id(11)).feedbackKind()).isEqualTo(StageFeedbackKind.SYSTEM_ERROR);
    }

    private static WorkspaceResponse workspace(WorkspaceRequest request) {
        return new WorkspaceResponse(UUID.randomUUID().toString(), request.generation(), initial());
    }

    private static RepositorySnapshot initial() {
        String c1 = "a".repeat(40);
        String c0 = "b".repeat(40);
        return new RepositorySnapshot(c1, "bad-tree", "safe-tree", List.of(c0), true, false, List.of(c1, c0));
    }

    private static String id(int value) {
        return String.format("00000000-0000-0000-0000-%012d", value);
    }
}
