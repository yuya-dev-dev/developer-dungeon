package jp.yuya.dev.developerdungeon.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import jp.yuya.dev.developerdungeon.contract.CommandKind;
import jp.yuya.dev.developerdungeon.contract.CommandResponse;
import jp.yuya.dev.developerdungeon.contract.ExecuteRequest;
import jp.yuya.dev.developerdungeon.contract.FileContentResponse;
import jp.yuya.dev.developerdungeon.contract.RepositorySnapshot;
import jp.yuya.dev.developerdungeon.contract.WorkspaceResponse;
import jp.yuya.dev.developerdungeon.contract.WriteFileRequest;
import jp.yuya.dev.developerdungeon.contract.WriteFileResponse;
import org.junit.jupiter.api.Test;

class StageFourServiceTest {
    private static final String C0 = "0".repeat(40);
    private static final String C1 = "1".repeat(40);
    private static final String C2 = "2".repeat(40);
    private static final String MERGE = "3".repeat(40);
    private static final String PATH = "src/main/resources/messages.properties";
    private static final String MAIN_BLOB = "a6306bacd230ac74aaf017cde7717bc3eb83684c";
    private static final String FINAL_BLOB = "e9de6755c74ff6bee8b96abcccfec27a06f23881";
    private static final String MAIN_TREE = "63ec3ef493c5b54618798e50fe8d2e58bc40a4a9";
    private static final String FEATURE_TREE = "e4d8a76dfb74d699e48a7437d60811202ba7face";
    private static final String FINAL_TREE = "0c0ff72db8de95d04ed1169388a4f345c870d686";

    @Test void resolvesTheConflictWithTheFixedEditorAndCreatesTheExpectedMergeCommit() {
        RunnerClient runner = mock(RunnerClient.class);
        when(runner.create(any())).thenReturn(new WorkspaceResponse("11111111-1111-1111-1111-111111111111", 0, initial()));
        when(runner.execute(any())).thenReturn(command(1, conflicted()), command(0, staged()), command(0, cleared()));
        when(runner.writeFile(any())).thenReturn(new WriteFileResponse(true, "b".repeat(64), edited()));
        StageService service = new StageService(runner, new StageRules(), new OutputSanitizer());

        service.open("STAGE-GIT-04");
        service.execute("STAGE-GIT-04", "git merge feature/profile-message", id(1));
        service.edit("STAGE-GIT-04", "profile.description=Manage security settings and edit your public profile.\r\n",
                "a".repeat(64), id(2));
        service.execute("STAGE-GIT-04", "git add " + PATH, id(3));
        StageView result = service.execute("STAGE-GIT-04", "git commit --no-edit", id(4));

        assertThat(result.cleared()).isTrue();
        var commands = org.mockito.ArgumentCaptor.forClass(ExecuteRequest.class);
        verify(runner, times(3)).execute(commands.capture());
        assertThat(commands.getAllValues().stream().map(request -> request.command().kind()))
                .containsExactly(CommandKind.MERGE_PROFILE_MESSAGE, CommandKind.ADD_PROFILE_MESSAGES, CommandKind.COMMIT_NO_EDIT);
        var writes = org.mockito.ArgumentCaptor.forClass(WriteFileRequest.class);
        verify(runner).writeFile(writes.capture());
        assertThat(writes.getValue().content()).isEqualTo("profile.description=Manage security settings and edit your public profile.\n");
    }

    @Test void issuesSeparateReadAndWriteRequestIds() {
        RunnerClient runner = mock(RunnerClient.class);
        when(runner.create(any())).thenReturn(new WorkspaceResponse("11111111-1111-1111-1111-111111111111", 0, initial()));
        when(runner.readFile(any())).thenReturn(new FileContentResponse("profile.description=Manage security settings.\n", "a".repeat(64)));
        StageService service = new StageService(runner, new StageRules(), new OutputSanitizer());

        StageView commandView = service.open("STAGE-GIT-04");
        StageEditorView editor = service.editor("STAGE-GIT-04");

        assertThat(editor.requestId()).isNotEqualTo(commandView.requestId());
        var reads = org.mockito.ArgumentCaptor.forClass(jp.yuya.dev.developerdungeon.contract.ReadFileRequest.class);
        verify(runner).readFile(reads.capture());
        assertThat(editor.requestId()).isNotEqualTo(reads.getValue().requestId());
    }

    @Test void rejectsInvalidEditorTextBeforeCallingRunner() {
        assertThatThrownBy(() -> StageEditorContentPolicy.normalize("bad\uD800value"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("UTF-8");
        assertThatThrownBy(() -> StageEditorContentPolicy.normalize("bad\u001bvalue"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("制御文字");
        assertThat(StageEditorContentPolicy.normalize("a\r\nb\r\n")).isEqualTo("a\nb\n");
    }

    @Test void doesNotClearWithReversedParentsOrUnexpectedTree() {
        StageRules rules = new StageRules();
        StageDefinition definition = rules.definition("STAGE-GIT-04");
        StageRules.StageTargets targets = rules.capture(definition, initial());

        assertThat(rules.grade(definition, finalSnapshot(List.of(C2, C1), FINAL_TREE), targets, 0, 0).cleared()).isFalse();
        assertThat(rules.grade(definition, finalSnapshot(List.of(C1, C2), "f".repeat(40)), targets, 0, 0).cleared()).isFalse();
    }

    private static CommandResponse command(int exit, RepositorySnapshot snapshot) {
        return new CommandResponse(exit, "", "", false, 1, snapshot);
    }
    private static RepositorySnapshot initial() {
        return snapshot(C1, MAIN_TREE, List.of(C0), true, false, MAIN_BLOB, List.of(), List.of(), List.of());
    }
    private static RepositorySnapshot conflicted() {
        return snapshot(C1, MAIN_TREE, List.of(C0), false, true, "4".repeat(40), List.of(PATH), List.of(PATH), List.of(PATH));
    }
    private static RepositorySnapshot edited() {
        return snapshot(C1, MAIN_TREE, List.of(C0), false, true, FINAL_BLOB, List.of(PATH), List.of(PATH), List.of(PATH));
    }
    private static RepositorySnapshot staged() {
        return snapshot(C1, MAIN_TREE, List.of(C0), false, true, FINAL_BLOB, List.of(), List.of(PATH), List.of());
    }
    private static RepositorySnapshot cleared() { return finalSnapshot(List.of(C1, C2), FINAL_TREE); }
    private static RepositorySnapshot finalSnapshot(List<String> parents, String tree) {
        return new RepositorySnapshot(MERGE, tree, MAIN_TREE, parents, true, false, List.of(MERGE, C1, C2, C0),
                "main", "", "", false, false, false, RepositorySnapshot.StageThreeState.empty(),
                new RepositorySnapshot.StageFourState(MERGE, C1, C2, C0, tree, FEATURE_TREE, FINAL_BLOB,
                        List.of(), List.of(), List.of(), List.of()));
    }
    private static RepositorySnapshot snapshot(String head, String tree, List<String> parents, boolean clean, boolean merge,
                                                 String blob, List<String> working, List<String> index, List<String> unmerged) {
        return new RepositorySnapshot(head, tree, parents.isEmpty() ? "" : MAIN_TREE, parents, clean, false,
                List.of(head, C0), "main", "", "", false, merge, false, RepositorySnapshot.StageThreeState.empty(),
                new RepositorySnapshot.StageFourState(C1, C0, C2, C0, MAIN_TREE, FEATURE_TREE, blob,
                        working, index, unmerged, List.of()));
    }
    private static String id(int value) { return String.format("00000000-0000-0000-0000-%012d", value); }
}
