package jp.yuya.dev.developerdungeon.runner;

import jp.yuya.dev.developerdungeon.contract.CommandKind;
import jp.yuya.dev.developerdungeon.contract.GitCommand;
import org.springframework.stereotype.Component;

@Component
class RunnerCommandValidator {
    void validate(GitCommand command) {
        if (command == null || command.kind() == null) throw new IllegalArgumentException("command is required");
        var ids = command.objectIds();
        if (command.kind() == CommandKind.STATUS || command.kind() == CommandKind.LOG_ONELINE) {
            if (!ids.isEmpty()) throw new IllegalArgumentException("object IDs are not allowed");
            return;
        }
        if ((command.kind() != CommandKind.SHOW && command.kind() != CommandKind.REVERT_NO_EDIT)
                || ids.size() != 1 || !ids.getFirst().matches("[0-9a-f]{40}")) {
            throw new IllegalArgumentException("invalid Git command");
        }
    }
}
