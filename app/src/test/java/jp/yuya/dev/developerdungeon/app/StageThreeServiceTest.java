package jp.yuya.dev.developerdungeon.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import jp.yuya.dev.developerdungeon.contract.CommandKind;
import jp.yuya.dev.developerdungeon.contract.CommandResponse;
import jp.yuya.dev.developerdungeon.contract.ExecuteRequest;
import jp.yuya.dev.developerdungeon.contract.RepositorySnapshot;
import jp.yuya.dev.developerdungeon.contract.WorkspaceResponse;
import org.junit.jupiter.api.Test;

class StageThreeServiceTest {
    private static final String C0 = "a".repeat(40);
    private static final String C1 = "b".repeat(40);
    private static final String INITIAL_BLOB = "80b018f3f86a4e710347ea98c0b903a1c6fcd9e7";
    private static final String FINAL_BLOB = "0861e3929141f32f4e5c8bcd68fc03173a3e3c8e";

    @Test void clearsOnlyWhenTheStashedWorkIsRestoredOnFeatureSearch() {
        RunnerClient runner = mock(RunnerClient.class);
        when(runner.create(any())).thenReturn(new WorkspaceResponse("11111111-1111-1111-1111-111111111111", 0, initial()));
        when(runner.execute(any())).thenReturn(response(stashed()), response(onSearchWithStash()), response(finalState()));
        StageService service = new StageService(runner, new StageRules(), new OutputSanitizer());

        service.open("STAGE-GIT-03");
        service.execute("STAGE-GIT-03", "git stash push", id(1));
        service.execute("STAGE-GIT-03", "git switch feature/search", id(2));
        var cleared = service.execute("STAGE-GIT-03", "git stash pop", id(3));

        assertThat(cleared.cleared()).isTrue();
        var commands = org.mockito.ArgumentCaptor.forClass(ExecuteRequest.class);
        verify(runner, org.mockito.Mockito.times(3)).execute(commands.capture());
        assertThat(commands.getAllValues().stream().map(request -> request.command().kind()))
                .containsExactly(CommandKind.STASH_PUSH, CommandKind.SWITCH, CommandKind.STASH_POP);
    }

    @Test void rejectsStashOptionsBeforeCallingRunner() {
        RunnerClient runner = mock(RunnerClient.class);
        when(runner.create(any())).thenReturn(new WorkspaceResponse("11111111-1111-1111-1111-111111111111", 0, initial()));
        StageService service = new StageService(runner, new StageRules(), new OutputSanitizer());

        service.open("STAGE-GIT-03");
        var rejected = service.execute("STAGE-GIT-03", "git stash push -u", id(1));

        assertThat(rejected.output()).contains("構文または引数", "ヒント").doesNotContain("git status /", "許可されたGitコマンド");
        assertThat(rejected.feedbackKind()).isEqualTo(StageFeedbackKind.INPUT_REJECTED);
        org.mockito.Mockito.verify(runner, org.mockito.Mockito.never()).execute(any());
    }

    @Test void rejectsMergeAndRebaseStatesEvenWhenOtherStageThreeConditionsMatch() {
        StageRules rules = new StageRules();
        StageDefinition definition = rules.definition("STAGE-GIT-03");
        StageRules.StageTargets targets = rules.capture(definition, initial());

        assertThat(rules.grade(definition, withState(finalState(), true, false), targets, 0, 0).cleared()).isFalse();
        assertThat(rules.grade(definition, withState(finalState(), false, true), targets, 0, 0).cleared()).isFalse();
    }

    @Test void exposesExactStashSyntaxOnlyFromHintThree() {
        StageRules rules = new StageRules();
        StageDefinition definition = rules.definition("STAGE-GIT-03");
        StageRules.StageTargets targets = rules.capture(definition, initial());

        assertThat(rules.hints(definition, 2, targets).toString()).doesNotContain("git stash push");
        assertThat(rules.hints(definition, 3, targets).getFirst()).contains("git stash push", "git switch <branch>", "git stash pop");
    }

    private static CommandResponse response(RepositorySnapshot snapshot) { return new CommandResponse(0, "", "", false, 1, snapshot); }
    private static RepositorySnapshot initial() { return snapshot(C0, "main", false, INITIAL_BLOB, List.of("search.txt"), List.of()); }
    private static RepositorySnapshot stashed() { return snapshot(C0, "main", true, "c".repeat(40), List.of(), List.of("d".repeat(40))); }
    private static RepositorySnapshot onSearchWithStash() { return snapshot(C1, "feature/search", true, "e".repeat(40), List.of(), List.of("d".repeat(40))); }
    private static RepositorySnapshot finalState() { return snapshot(C1, "feature/search", false, FINAL_BLOB, List.of("search.txt"), List.of()); }
    private static RepositorySnapshot withState(RepositorySnapshot source, boolean merge, boolean rebase) {
        return new RepositorySnapshot(source.headObjectId(), source.headTreeId(), source.firstParentTreeId(), source.headParents(), source.clean(), source.revertInProgress(),
                source.ancestorObjectIds(), source.currentBranch(), source.featureProfileTip(), source.featureNotificationTip(), source.cherryPickInProgress(), merge, rebase, source.stageThree());
    }
    private static RepositorySnapshot snapshot(String head, String branch, boolean clean, String blob, List<String> working, List<String> stash) {
        var state = new RepositorySnapshot.StageThreeState(C0, C1, C0, blob, working, List.of(), List.of(), List.of(), stash);
        return new RepositorySnapshot(head, "tree", "base", head.equals(C1) ? List.of(C0) : List.of(), clean, false, List.of(head, C0), branch, "", "", false, false, false, state);
    }
    private static String id(int value) { return String.format("00000000-0000-0000-0000-%012d", value); }
}
