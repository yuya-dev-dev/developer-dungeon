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
        assertThatCode(() -> validator.validate(new GitCommand(CommandKind.STASH_APPLY))).doesNotThrowAnyException();
        assertThatCode(() -> validator.validate(new GitCommand(CommandKind.STASH_DROP))).doesNotThrowAnyException();
        assertThatCode(() -> validator.validate(new GitCommand(CommandKind.LOG_GRAPH_ALL))).doesNotThrowAnyException();
        assertThatCode(() -> validator.validate(new GitCommand(CommandKind.MERGE_PROFILE_MESSAGE))).doesNotThrowAnyException();
        assertThatCode(() -> validator.validate(new GitCommand(CommandKind.ADD_PROFILE_MESSAGES))).doesNotThrowAnyException();
        assertThatCode(() -> validator.validate(new GitCommand(CommandKind.COMMIT_NO_EDIT))).doesNotThrowAnyException();
        assertThatCode(() -> validator.validate(new GitCommand(CommandKind.COMMIT_RESTORE_SETTINGS))).doesNotThrowAnyException();
        assertThatCode(() -> validator.validate(new GitCommand(CommandKind.COMMIT_ALL_NO_EDIT))).doesNotThrowAnyException();
        assertThatCode(() -> validator.validate(new GitCommand(CommandKind.REFLOG_HEAD))).doesNotThrowAnyException();
        assertThatCode(() -> validator.validate(new GitCommand(CommandKind.SWITCH_PAYMENT_RETRY))).doesNotThrowAnyException();
        assertThatCode(() -> validator.validate(new GitCommand(CommandKind.CREATE_PAYMENT_RETRY_BRANCH, "a".repeat(40)))).doesNotThrowAnyException();
        assertThatCode(() -> validator.validate(new GitCommand(CommandKind.REVERT_NO_COMMIT, "a".repeat(40)))).doesNotThrowAnyException();
        assertThatCode(() -> validator.validate(new GitCommand(CommandKind.SWITCH_CREATE_PAYMENT_RETRY, "a".repeat(40)))).doesNotThrowAnyException();
        for (CommandKind kind : new CommandKind[]{CommandKind.ADD_TRAINING_INTRO, CommandKind.COMMIT_TRAINING_ONE,
                CommandKind.UNSTAGE_TRAINING_REPORT, CommandKind.ADD_TRAINING_IGNORE, CommandKind.ADD_TRAINING_CONFIG,
                CommandKind.COMMIT_TRAINING_TWO, CommandKind.SWITCH_CREATE_TRAINING_BRANCH,
                CommandKind.SWITCH_TRAINING_BRANCH, CommandKind.ADD_TRAINING_HANDOFF, CommandKind.COMMIT_TRAINING_THREE}) {
            assertThatCode(() -> validator.validate(new GitCommand(kind))).doesNotThrowAnyException();
        }
    }
    @Test void rejectsRevisionSyntaxAndShortIds() {
        assertThatThrownBy(() -> validator.validate(new GitCommand(CommandKind.SHOW, "HEAD^"))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> validator.validate(new GitCommand(CommandKind.REVERT_NO_EDIT, "a".repeat(12)))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> validator.validate(new GitCommand(CommandKind.REVERT_NO_COMMIT, "a".repeat(12)))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> validator.validate(new GitCommand(CommandKind.SWITCH_CREATE_PAYMENT_RETRY, "a".repeat(12)))).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void keepsBranchTargetsSeparateFromObjectTargets() {
        assertThatCode(() -> validator.validate(GitCommand.switchTo("feature/notification"))).doesNotThrowAnyException();
        assertThatThrownBy(() -> validator.validate(new GitCommand(CommandKind.SWITCH, "a".repeat(40), "feature/notification"))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> validator.validate(new GitCommand(CommandKind.CHERRY_PICK, "a".repeat(40), "feature/notification"))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> validator.validate(GitCommand.switchTo("feature/unknown"))).isInstanceOf(IllegalArgumentException.class);
        assertThatCode(() -> validator.validate(GitCommand.switchTo("feature/search"))).doesNotThrowAnyException();
        assertThatThrownBy(() -> validator.validate(new GitCommand(CommandKind.STASH_PUSH, "a".repeat(40)))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> validator.validate(new GitCommand(CommandKind.REFLOG_HEAD, "a".repeat(40)))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> validator.validate(new GitCommand(CommandKind.SWITCH_PAYMENT_RETRY, null, "feature/payment-retry"))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> validator.validate(new GitCommand(CommandKind.CREATE_PAYMENT_RETRY_BRANCH, "a".repeat(12)))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> validator.validate(new GitCommand(CommandKind.ADD_TRAINING_INTRO, "a".repeat(40))))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
