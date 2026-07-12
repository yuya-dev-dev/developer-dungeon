package jp.yuya.dev.developerdungeon.app;

import java.util.List;
import jp.yuya.dev.developerdungeon.contract.CommandKind;
import jp.yuya.dev.developerdungeon.contract.GitCommand;
import org.springframework.stereotype.Component;

@Component
class GitCommandParser {
    GitCommand parse(String raw) {
        if (raw == null || raw.length() > 512 || raw.indexOf('\n') >= 0 || raw.indexOf('\r') >= 0
                || raw.matches(".*[;|&<>`].*") || raw.contains("$()") || raw.contains("\"") || raw.contains("'")) {
            throw new IllegalArgumentException("許可されていない入力です。");
        }
        return switch (raw) {
            case "git status" -> new GitCommand(CommandKind.STATUS, List.of());
            case "git log --oneline" -> new GitCommand(CommandKind.LOG_ONELINE, List.of());
            default -> parseObjectCommand(raw);
        };
    }

    private GitCommand parseObjectCommand(String raw) {
        if (raw.matches("git show [0-9a-f]{12}([0-9a-f]{28})?")) return new GitCommand(CommandKind.SHOW, List.of(raw.substring(9)));
        if (raw.matches("git revert --no-edit [0-9a-f]{12}([0-9a-f]{28})?")) return new GitCommand(CommandKind.REVERT_NO_EDIT, List.of(raw.substring(21)));
        throw new IllegalArgumentException("このステージで許可されたGitコマンドではありません。");
    }
}
