package jp.yuya.dev.developerdungeon.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import jp.yuya.dev.developerdungeon.contract.CommandKind;
import jp.yuya.dev.developerdungeon.contract.CommandResponse;
import jp.yuya.dev.developerdungeon.contract.ExecuteRequest;
import jp.yuya.dev.developerdungeon.contract.RepositorySnapshot;
import jp.yuya.dev.developerdungeon.contract.WorkspaceResponse;
import org.junit.jupiter.api.Test;

class StageFiveServiceTest {
    private static final String C0 = "4b03c129e4d5b2bfe41fb2afd208b13dab7824a1";
    private static final String C1 = "39194dda957695ace62387ecdc5f77fcd5ee81ea";
    private static final String TREE = "cbc2826a3bd49e4947b67c020e61dd5e4ca7adb3";

    @Test void recoversAfterStatusAndNormalLogThenTheFixedReflogDisplaysTheCommit() {
        RunnerClient runner = mock(RunnerClient.class);
        when(runner.create(any())).thenReturn(new WorkspaceResponse("11111111-1111-1111-1111-111111111111", 0, initial()));
        when(runner.execute(any())).thenReturn(response("On branch main", initial()),
                response(C0.substring(0, 12) + " C0: keep production stable\n", initial()),
                response(reflogOutput(), initial()), response("", branchCreated()), response("", cleared()));
        StageService service = new StageService(runner, new StageRules(), new OutputSanitizer());

        service.open("STAGE-GIT-05");
        service.execute("STAGE-GIT-05", "git status", id(1));
        service.execute("STAGE-GIT-05", "git log --oneline --all --decorate", id(2));
        service.execute("STAGE-GIT-05", "git reflog", id(3));
        service.execute("STAGE-GIT-05", "git branch feature/payment-retry " + C1.substring(0, 12), id(4));
        StageView result = service.execute("STAGE-GIT-05", "git switch feature/payment-retry", id(5));

        assertThat(result.cleared()).isTrue();
        var commands = org.mockito.ArgumentCaptor.forClass(ExecuteRequest.class);
        verify(runner, times(5)).execute(commands.capture());
        assertThat(commands.getAllValues().stream().map(request -> request.command().kind()))
                .containsExactly(CommandKind.STATUS, CommandKind.LOG_ONELINE_ALL_DECORATE, CommandKind.REFLOG_HEAD,
                        CommandKind.CREATE_PAYMENT_RETRY_BRANCH, CommandKind.SWITCH_PAYMENT_RETRY);
        assertThat(commands.getAllValues().get(3).command().objectId()).isEqualTo(C1);
    }

    @Test void recoversAfterReflogThenShowAndStatus() {
        RunnerClient runner = mock(RunnerClient.class);
        when(runner.create(any())).thenReturn(new WorkspaceResponse("11111111-1111-1111-1111-111111111111", 0, initial()));
        when(runner.execute(any())).thenReturn(response(reflogOutput(), initial()),
                response(C1.substring(0, 12) + " C1: payment retry\n", initial()), response("On branch main", initial()),
                response("", branchCreated()), response("", cleared()));
        StageService service = new StageService(runner, new StageRules(), new OutputSanitizer());

        service.open("STAGE-GIT-05");
        service.execute("STAGE-GIT-05", "git reflog", id(10));
        service.execute("STAGE-GIT-05", "git show " + C1.substring(0, 12), id(11));
        service.execute("STAGE-GIT-05", "git status", id(12));
        service.execute("STAGE-GIT-05", "git branch feature/payment-retry " + C1.substring(0, 12), id(13));
        StageView result = service.execute("STAGE-GIT-05", "git switch feature/payment-retry", id(14));

        assertThat(result.cleared()).isTrue();
        var commands = org.mockito.ArgumentCaptor.forClass(ExecuteRequest.class);
        verify(runner, times(5)).execute(commands.capture());
        assertThat(commands.getAllValues().stream().map(request -> request.command().kind()))
                .containsExactly(CommandKind.REFLOG_HEAD, CommandKind.SHOW, CommandKind.STATUS,
                        CommandKind.CREATE_PAYMENT_RETRY_BRANCH, CommandKind.SWITCH_PAYMENT_RETRY);
        assertThat(commands.getAllValues().get(3).command().objectId()).isEqualTo(C1);
    }

    @Test void doesNotAuthorizeAnIdFromFailedOrTruncatedReflogOutput() {
        RunnerClient runner = mock(RunnerClient.class);
        when(runner.create(any())).thenReturn(new WorkspaceResponse("11111111-1111-1111-1111-111111111111", 0, initial()));
        when(runner.execute(any())).thenReturn(new CommandResponse(1, C1.substring(0, 12) + "\tcommit: C1: payment retry\n", "", true, 1, initial()));
        StageService service = new StageService(runner, new StageRules(), new OutputSanitizer());

        service.open("STAGE-GIT-05");
        service.execute("STAGE-GIT-05", "git reflog", id(1));
        StageView rejected = service.execute("STAGE-GIT-05", "git branch feature/payment-retry " + C1.substring(0, 12), id(2));

        assertThat(rejected.output()).contains("確認済み").doesNotContain("git branch feature/payment-retry");
        assertThat(rejected.feedbackKind()).isEqualTo(StageFeedbackKind.INPUT_REJECTED);
        verify(runner).execute(any());
    }

    @Test void doesNotAuthorizeTheRecoveryCommitFromNormalLogOutput() {
        RunnerClient runner = mock(RunnerClient.class);
        when(runner.create(any())).thenReturn(new WorkspaceResponse("11111111-1111-1111-1111-111111111111", 0, initial()));
        when(runner.execute(any())).thenReturn(response(C1.substring(0, 12) + " C1: payment retry\n", initial()));
        StageService service = new StageService(runner, new StageRules(), new OutputSanitizer());

        service.open("STAGE-GIT-05");
        service.execute("STAGE-GIT-05", "git log --oneline --all --decorate", id(1));
        StageView rejected = service.execute("STAGE-GIT-05", "git branch feature/payment-retry " + C1.substring(0, 12), id(2));

        assertThat(rejected.output()).contains("確認済み").doesNotContain("git branch feature/payment-retry");
        assertThat(rejected.feedbackKind()).isEqualTo(StageFeedbackKind.INPUT_REJECTED);
        verify(runner).execute(any());
    }

    private static CommandResponse response(String output, RepositorySnapshot snapshot) {
        return new CommandResponse(0, output, "", false, 1, snapshot);
    }
    private static String reflogOutput() {
        return """
                4b03c129e4d5\tcheckout: moving from feature/payment-retry to main
                39194dda9576\tcommit: C1: payment retry
                """;
    }
    private static RepositorySnapshot initial() { return snapshot("main", C0, null, List.of("main")); }
    private static RepositorySnapshot branchCreated() { return snapshot("main", C0, C1, List.of("feature/payment-retry", "main")); }
    private static RepositorySnapshot cleared() { return snapshot("feature/payment-retry", C1, C1, List.of("feature/payment-retry", "main")); }
    private static RepositorySnapshot snapshot(String branch, String head, String retryTip, List<String> branches) {
        return new RepositorySnapshot(head, head.equals(C1) ? TREE : "0".repeat(40), "", List.of(), true, false, List.of(head), branch,
                "", "", false, false, false, RepositorySnapshot.StageThreeState.empty(), RepositorySnapshot.StageFourState.empty(),
                new RepositorySnapshot.StageFiveState(C0, C1, C0, TREE, retryTip, branches));
    }
    private static String id(int value) { return String.format("00000000-0000-0000-0000-%012d", value); }
}
