package jp.yuya.dev.developerdungeon.runner;

import static org.assertj.core.api.Assertions.*;
import jp.yuya.dev.developerdungeon.contract.CommandKind;
import jp.yuya.dev.developerdungeon.contract.GitCommand;
import org.junit.jupiter.api.Test;

class RunnerCommandValidatorTest {
    private final RunnerCommandValidator validator = new RunnerCommandValidator();
    @Test void acceptsOnlyExpectedShapes() {
        assertThatCode(() -> validator.validate(new GitCommand(CommandKind.STATUS))).doesNotThrowAnyException();
        assertThatCode(() -> validator.validate(new GitCommand(CommandKind.DIFF))).doesNotThrowAnyException();
        assertThatCode(() -> validator.validate(new GitCommand(CommandKind.DIFF_STAGED))).doesNotThrowAnyException();
        assertThatCode(() -> validator.validate(new GitCommand(CommandKind.STASH_PUSH))).doesNotThrowAnyException();
        assertThatCode(() -> validator.validate(new GitCommand(CommandKind.STASH_LIST))).doesNotThrowAnyException();
        assertThatCode(() -> validator.validate(new GitCommand(CommandKind.STASH_POP))).doesNotThrowAnyException();
        assertThatCode(() -> validator.validate(new GitCommand(CommandKind.LOG_GRAPH_ALL))).doesNotThrowAnyException();
        assertThatCode(() -> validator.validate(new GitCommand(CommandKind.MERGE_PROFILE_MESSAGE))).doesNotThrowAnyException();
        assertThatCode(() -> validator.validate(new GitCommand(CommandKind.ADD_PROFILE_MESSAGES))).doesNotThrowAnyException();
        assertThatCode(() -> validator.validate(new GitCommand(CommandKind.COMMIT_NO_EDIT))).doesNotThrowAnyException();
    }
    @Test void rejectsRevisionSyntaxAndShortIds() {
        assertThatThrownBy(() -> validator.validate(new GitCommand(CommandKind.SHOW, "HEAD^"))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> validator.validate(new GitCommand(CommandKind.REVERT_NO_EDIT, "a".repeat(12)))).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void keepsBranchTargetsSeparateFromObjectTargets() {
        assertThatCode(() -> validator.validate(GitCommand.switchTo("feature/notification"))).doesNotThrowAnyException();
        assertThatThrownBy(() -> validator.validate(new GitCommand(CommandKind.SWITCH, "a".repeat(40), "feature/notification"))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> validator.validate(new GitCommand(CommandKind.CHERRY_PICK, "a".repeat(40), "feature/notification"))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> validator.validate(GitCommand.switchTo("feature/unknown"))).isInstanceOf(IllegalArgumentException.class);
        assertThatCode(() -> validator.validate(GitCommand.switchTo("feature/search"))).doesNotThrowAnyException();
        assertThatThrownBy(() -> validator.validate(new GitCommand(CommandKind.STASH_PUSH, "a".repeat(40)))).isInstanceOf(IllegalArgumentException.class);
    }
}
