package jp.yuya.dev.developerdungeon.app;

import static org.assertj.core.api.Assertions.*;
import jp.yuya.dev.developerdungeon.contract.CommandKind;
import org.junit.jupiter.api.Test;

class GitCommandParserTest {
    private final GitCommandParser parser = new GitCommandParser();
    @Test void acceptsExactStatus() { assertThat(parser.parse("git status").kind()).isEqualTo(CommandKind.STATUS); }
    @Test void acceptsOnlyTwelveOrFortyLowercaseObjectIds() {
        String id = "a".repeat(40);
        var revert = parser.parse("git revert --no-edit " + id);
        assertThat(revert.kind()).isEqualTo(CommandKind.REVERT_NO_EDIT);
        assertThat(revert.objectIds()).containsExactly(id);
        assertThatThrownBy(() -> parser.parse("git revert " + id)).isInstanceOf(IllegalArgumentException.class);
        assertThat(parser.parse("git show " + "a".repeat(12)).kind()).isEqualTo(CommandKind.SHOW);
        assertThatThrownBy(() -> parser.parse("git show " + "a".repeat(11))).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void rejectsShellSyntaxAndLineBreaks() {
        assertThatThrownBy(() -> parser.parse("git status; whoami")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> parser.parse("git status\n git log --oneline")).isInstanceOf(IllegalArgumentException.class);
    }
}
