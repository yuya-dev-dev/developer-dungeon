package jp.yuya.dev.developerdungeon.runner;

import jp.yuya.dev.developerdungeon.contract.CommandKind;
import jp.yuya.dev.developerdungeon.contract.GitCommand;
import org.springframework.stereotype.Component;

@Component
class RunnerCommandValidator {
    void validate(GitCommand command) {
        if (command == null || command.kind() == null) throw new IllegalArgumentException("command is required");
        String objectId = command.objectId();
        String branchName = command.branchName();
        if (command.kind() == CommandKind.STATUS || command.kind() == CommandKind.LOG_ONELINE
                || command.kind() == CommandKind.LOG_ONELINE_ALL_DECORATE || command.kind() == CommandKind.BRANCH
                || command.kind() == CommandKind.DIFF || command.kind() == CommandKind.DIFF_STAGED
                || command.kind() == CommandKind.STASH_PUSH || command.kind() == CommandKind.STASH_LIST || command.kind() == CommandKind.STASH_POP
                || command.kind() == CommandKind.LOG_GRAPH_ALL || command.kind() == CommandKind.MERGE_PROFILE_MESSAGE
                || command.kind() == CommandKind.ADD_PROFILE_MESSAGES || command.kind() == CommandKind.COMMIT_NO_EDIT) {
            if (objectId != null || branchName != null) throw new IllegalArgumentException("command target is not allowed");
            return;
        }
        if (command.kind() == CommandKind.SWITCH) {
            if (objectId != null || branchName == null || !branchName.matches("feature/(profile|notification|search)")) {
                throw new IllegalArgumentException("invalid Git command");
            }
            return;
        }
        if ((command.kind() != CommandKind.SHOW && command.kind() != CommandKind.REVERT_NO_EDIT
                && command.kind() != CommandKind.CHERRY_PICK && command.kind() != CommandKind.RESET_HARD)
                || branchName != null || objectId == null || !objectId.matches("[0-9a-f]{40}")) {
            throw new IllegalArgumentException("invalid Git command");
        }
    }
}
