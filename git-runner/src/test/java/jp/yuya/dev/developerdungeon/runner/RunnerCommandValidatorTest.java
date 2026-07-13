package jp.yuya.dev.developerdungeon.runner;

import static org.assertj.core.api.Assertions.*;
import jp.yuya.dev.developerdungeon.contract.CommandKind;
import jp.yuya.dev.developerdungeon.contract.GitCommand;
import org.junit.jupiter.api.Test;

class RunnerCommandValidatorTest {
    private final RunnerCommandValidator validator = new RunnerCommandValidator();
    @Test void acceptsOnlyExpectedShapes() { assertThatCode(() -> validator.validate(new GitCommand(CommandKind.STATUS))).doesNotThrowAnyException(); }
    @Test void rejectsRevisionSyntaxAndShortIds() {
        assertThatThrownBy(() -> validator.validate(new GitCommand(CommandKind.SHOW, "HEAD^"))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> validator.validate(new GitCommand(CommandKind.REVERT_NO_EDIT, "a".repeat(12)))).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void keepsBranchTargetsSeparateFromObjectTargets() {
        assertThatCode(() -> validator.validate(GitCommand.switchTo("feature/notification"))).doesNotThrowAnyException();
        assertThatThrownBy(() -> validator.validate(new GitCommand(CommandKind.SWITCH, "a".repeat(40), "feature/notification"))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> validator.validate(new GitCommand(CommandKind.CHERRY_PICK, "a".repeat(40), "feature/notification"))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> validator.validate(GitCommand.switchTo("feature/unknown"))).isInstanceOf(IllegalArgumentException.class);
    }
}
