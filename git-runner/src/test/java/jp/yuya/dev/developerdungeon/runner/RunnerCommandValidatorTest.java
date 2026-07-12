package jp.yuya.dev.developerdungeon.runner;

import static org.assertj.core.api.Assertions.*;
import java.util.List;
import jp.yuya.dev.developerdungeon.contract.CommandKind;
import jp.yuya.dev.developerdungeon.contract.GitCommand;
import org.junit.jupiter.api.Test;

class RunnerCommandValidatorTest {
    private final RunnerCommandValidator validator = new RunnerCommandValidator();
    @Test void acceptsOnlyExpectedShapes() { assertThatCode(() -> validator.validate(new GitCommand(CommandKind.STATUS, List.of()))).doesNotThrowAnyException(); }
    @Test void rejectsRevisionSyntaxAndShortIds() {
        assertThatThrownBy(() -> validator.validate(new GitCommand(CommandKind.SHOW, List.of("HEAD^")))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> validator.validate(new GitCommand(CommandKind.REVERT_NO_EDIT, List.of("a".repeat(12))))).isInstanceOf(IllegalArgumentException.class);
    }
}
