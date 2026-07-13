package jp.yuya.dev.developerdungeon.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.util.List;
import jp.yuya.dev.developerdungeon.contract.CommandKind;
import jp.yuya.dev.developerdungeon.contract.CommandResponse;
import jp.yuya.dev.developerdungeon.contract.ExecuteRequest;
import jp.yuya.dev.developerdungeon.contract.RepositorySnapshot;
import jp.yuya.dev.developerdungeon.contract.WorkspaceRequest;
import jp.yuya.dev.developerdungeon.contract.WorkspaceResponse;
import org.junit.jupiter.api.Test;

class StageTwoServiceTest {
    private static final String C0 = "a".repeat(40);
    private static final String C1 = "b".repeat(40);
    private static final String PICKED = "c".repeat(40);
    private static final String BASE_TREE = "d".repeat(40);
    private static final String NOTIFICATION_TREE = "e".repeat(40);

    @Test void hintFourMakesFixedC0AndC1UsableWithoutRunningLog() {
        RunnerClient runner = mock(RunnerClient.class);
        when(runner.create(any())).thenReturn(new WorkspaceResponse("11111111-1111-1111-1111-111111111111", 0, profileSnapshot(C1, C0, C1)));
        when(runner.execute(any())).thenReturn(response(notificationSnapshot(C0, C1, C0)), response(notificationSnapshot(PICKED, C1, PICKED)),
                response(profileSnapshot(C1, PICKED, C1)), response(profileSnapshot(C0, PICKED, C0)), response(notificationSnapshot(PICKED, C0, PICKED)));
        StageService service = new StageService(runner, new StageRules(), new OutputSanitizer());

        service.open("STAGE-GIT-02");
        service.hint("STAGE-GIT-02"); service.hint("STAGE-GIT-02"); service.hint("STAGE-GIT-02"); service.hint("STAGE-GIT-02");
        service.execute("STAGE-GIT-02", "git switch feature/notification", id(1));
        service.execute("STAGE-GIT-02", "git cherry-pick " + C1.substring(0, 12), id(2));
        service.execute("STAGE-GIT-02", "git switch feature/profile", id(3));
        service.execute("STAGE-GIT-02", "git reset --hard " + C0.substring(0, 12), id(4));
        var cleared = service.execute("STAGE-GIT-02", "git switch feature/notification", id(5));

        assertThat(cleared.cleared()).isTrue();
        var requests = org.mockito.ArgumentCaptor.forClass(ExecuteRequest.class);
        verify(runner, times(5)).execute(requests.capture());
        assertThat(requests.getAllValues().get(1).command().kind()).isEqualTo(CommandKind.CHERRY_PICK);
        assertThat(requests.getAllValues().get(1).command().objectId()).isEqualTo(C1);
        assertThat(requests.getAllValues().get(3).command().kind()).isEqualTo(CommandKind.RESET_HARD);
        assertThat(requests.getAllValues().get(3).command().objectId()).isEqualTo(C0);
    }

    @Test void rejectsC0CherryPickAndC1ResetBeforeCallingRunner() {
        RunnerClient runner = mock(RunnerClient.class);
        when(runner.create(any())).thenReturn(new WorkspaceResponse("11111111-1111-1111-1111-111111111111", 0, profileSnapshot(C1, C0, C1)));
        StageService service = new StageService(runner, new StageRules(), new OutputSanitizer());

        service.open("STAGE-GIT-02");
        service.hint("STAGE-GIT-02"); service.hint("STAGE-GIT-02"); service.hint("STAGE-GIT-02"); service.hint("STAGE-GIT-02");

        var wrongCherryPick = service.execute("STAGE-GIT-02", "git cherry-pick " + C0.substring(0, 12), id(1));
        var wrongReset = service.execute("STAGE-GIT-02", "git reset --hard " + C1.substring(0, 12), id(2));

        assertThat(wrongCherryPick.output()).contains("通知機能");
        assertThat(wrongReset.output()).contains("C0");
        verify(runner, never()).execute(any());
    }

    @Test void rejectsNotificationTreeWhenItsParentIsNotC0() {
        StageRules rules = new StageRules();
        StageDefinition definition = rules.definition("STAGE-GIT-02");
        StageRules.StageTargets targets = rules.capture(definition, profileSnapshot(C1, C0, C1));
        RepositorySnapshot wrongParent = new RepositorySnapshot(PICKED, NOTIFICATION_TREE, BASE_TREE, List.of(C1), true, false,
                List.of(PICKED, C1, C0), "feature/notification", C0, PICKED, false);

        assertThat(rules.grade(definition, wrongParent, targets, 0, 0).cleared()).isFalse();
    }

    @Test void rejectsRevertInProgressEvenWhenBranchAndTreeMatch() {
        StageRules rules = new StageRules();
        StageDefinition definition = rules.definition("STAGE-GIT-02");
        StageRules.StageTargets targets = rules.capture(definition, profileSnapshot(C1, C0, C1));
        RepositorySnapshot reverting = new RepositorySnapshot(PICKED, NOTIFICATION_TREE, BASE_TREE, List.of(C0), true, true,
                List.of(PICKED, C1, C0), "feature/notification", C0, PICKED, false);

        assertThat(rules.grade(definition, reverting, targets, 0, 0).cleared()).isFalse();
    }

    private static CommandResponse response(RepositorySnapshot snapshot) { return new CommandResponse(0, "", "", false, 1, snapshot); }
    private static RepositorySnapshot profileSnapshot(String profileTip, String notificationTip, String head) {
        return new RepositorySnapshot(head, head.equals(C1) ? NOTIFICATION_TREE : BASE_TREE, BASE_TREE, head.equals(C1) ? List.of(C0) : List.of(), true, false,
                List.of(C1, C0), "feature/profile", profileTip, notificationTip, false);
    }
    private static RepositorySnapshot notificationSnapshot(String head, String profileTip, String notificationTip) {
        return new RepositorySnapshot(head, head.equals(C0) ? BASE_TREE : NOTIFICATION_TREE, BASE_TREE, head.equals(C0) ? List.of() : List.of(C0), true, false,
                List.of(PICKED, C1, C0), "feature/notification", profileTip, notificationTip, false);
    }
    private static String id(int value) { return String.format("00000000-0000-0000-0000-%012d", value); }
}
