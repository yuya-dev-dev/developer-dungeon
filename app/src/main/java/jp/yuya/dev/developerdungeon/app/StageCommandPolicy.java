package jp.yuya.dev.developerdungeon.app;

import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import jp.yuya.dev.developerdungeon.contract.CommandKind;
import jp.yuya.dev.developerdungeon.contract.GitCommand;

final class StageCommandPolicy {
    private static final Pattern OBJECT_ID = Pattern.compile("[0-9a-f]{12}([0-9a-f]{28})?");
    private static final Pattern LOG_ID = Pattern.compile("^([0-9a-f]{12}) [^\\r\\n]*$");
    private static final Pattern REFLOG_ID = Pattern.compile("^([0-9a-f]{12})\\t[^\\r\\n]*$");
    private static final String STAGE_FOUR_PATH = "src/main/resources/messages.properties";

    GitCommand parse(StageDefinition definition, String raw) {
        rejectUnsafeRaw(raw);
        return switch (definition.key()) {
            case "STAGE-GIT-01" -> parseStageOne(raw);
            case "STAGE-GIT-02" -> parseStageTwo(raw);
            case "STAGE-GIT-03" -> parseStageThree(raw);
            case "STAGE-GIT-04" -> parseStageFour(raw);
            case "STAGE-GIT-05" -> parseStageFive(raw);
            case "TRAINING-GIT-01" -> parseTrainingOne(raw);
            case "TRAINING-GIT-02" -> parseTrainingTwo(raw);
            case "TRAINING-GIT-03" -> parseTrainingThree(raw);
            default -> throw new IllegalArgumentException("unknown stage");
        };
    }
    GitCommand normalize(StageDefinition definition, GitCommand command, StageRules.StageTargets targets, Set<String> displayed) {
        if (command.objectId() == null) return command;
        String normalized = exactAllowedObject(command.objectId(), targets, displayed);
        if ("STAGE-GIT-01".equals(definition.key())
                && (command.kind() == CommandKind.REVERT_NO_EDIT || command.kind() == CommandKind.REVERT_NO_COMMIT)
                && !normalized.equals(targets.primaryObjectId())) {
            throw new IllegalArgumentException("このステージで取り消せるcommitではありません。");
        }
        if ("STAGE-GIT-02".equals(definition.key())) {
            if (command.kind() == CommandKind.CHERRY_PICK && !normalized.equals(targets.primaryObjectId())) {
                throw new IllegalArgumentException("通知機能のcommitだけを移してください。");
            }
            if (command.kind() == CommandKind.RESET_HARD && !normalized.equals(targets.secondaryObjectId())) {
                throw new IllegalArgumentException("profileは元のC0へだけ戻してください。");
            }
        }
        if ("STAGE-GIT-05".equals(definition.key())
                && (command.kind() == CommandKind.CREATE_PAYMENT_RETRY_BRANCH
                || command.kind() == CommandKind.SWITCH_CREATE_PAYMENT_RETRY)
                && !normalized.equals(targets.primaryObjectId())) {
            throw new IllegalArgumentException("reflogで確認した復旧対象のcommitだけを指定してください。");
        }
        return new GitCommand(command.kind(), normalized, null);
    }
    void recordDisplayedObjects(StageDefinition definition, GitCommand command, String output, StageRules.StageTargets targets, Set<String> displayed) {
        if ("STAGE-GIT-05".equals(definition.key()) && command.kind() != CommandKind.REFLOG_HEAD) return;
        Pattern format = command.kind() == CommandKind.REFLOG_HEAD ? REFLOG_ID
                : (command.kind() == CommandKind.LOG_ONELINE || command.kind() == CommandKind.LOG_ONELINE_ALL_DECORATE ? LOG_ID : null);
        if (format == null) return;
        output.lines().map(format::matcher).filter(java.util.regex.Matcher::matches).map(matcher -> matcher.group(1))
                .filter(prefix -> targets.allowedObjects().stream().anyMatch(id -> id.startsWith(prefix))).forEach(displayed::add);
    }
    private GitCommand parseStageOne(String raw) {
        if ("git status".equals(raw)) return new GitCommand(CommandKind.STATUS);
        if ("git log --oneline".equals(raw)) return new GitCommand(CommandKind.LOG_ONELINE);
        if (raw.matches("git show " + OBJECT_ID.pattern())) return new GitCommand(CommandKind.SHOW, raw.substring(9));
        if (raw.matches("git revert --no-edit " + OBJECT_ID.pattern())) return new GitCommand(CommandKind.REVERT_NO_EDIT, raw.substring(21));
        if (raw.matches("git revert --no-commit " + OBJECT_ID.pattern())) return new GitCommand(CommandKind.REVERT_NO_COMMIT, raw.substring(23));
        if ("git commit -m restore-required-settings".equals(raw)) return new GitCommand(CommandKind.COMMIT_RESTORE_SETTINGS);
        throw unsupportedSyntax();
    }
    private GitCommand parseStageTwo(String raw) {
        if ("git status".equals(raw)) return new GitCommand(CommandKind.STATUS);
        if ("git log --oneline --all --decorate".equals(raw)) return new GitCommand(CommandKind.LOG_ONELINE_ALL_DECORATE);
        if ("git branch".equals(raw)) return new GitCommand(CommandKind.BRANCH);
        if (raw.matches("git show " + OBJECT_ID.pattern())) return new GitCommand(CommandKind.SHOW, raw.substring(9));
        if (raw.matches("git switch feature/(profile|notification)")) return GitCommand.switchTo(raw.substring(11));
        if (raw.matches("git cherry-pick " + OBJECT_ID.pattern())) return new GitCommand(CommandKind.CHERRY_PICK, raw.substring(16));
        if (raw.matches("git reset --hard " + OBJECT_ID.pattern())) return new GitCommand(CommandKind.RESET_HARD, raw.substring(17));
        throw unsupportedSyntax();
    }
    private GitCommand parseStageThree(String raw) {
        if ("git status".equals(raw)) return new GitCommand(CommandKind.STATUS);
        if ("git diff".equals(raw)) return new GitCommand(CommandKind.DIFF);
        if ("git diff --staged".equals(raw)) return new GitCommand(CommandKind.DIFF_STAGED);
        if ("git branch".equals(raw)) return new GitCommand(CommandKind.BRANCH);
        if ("git stash push".equals(raw)) return new GitCommand(CommandKind.STASH_PUSH);
        if ("git stash list".equals(raw)) return new GitCommand(CommandKind.STASH_LIST);
        if ("git switch feature/search".equals(raw)) return GitCommand.switchTo("feature/search");
        if ("git stash pop".equals(raw)) return new GitCommand(CommandKind.STASH_POP);
        if ("git stash apply".equals(raw)) return new GitCommand(CommandKind.STASH_APPLY);
        if ("git stash drop".equals(raw)) return new GitCommand(CommandKind.STASH_DROP);
        throw unsupportedSyntax();
    }
    private GitCommand parseStageFour(String raw) {
        if ("git status".equals(raw)) return new GitCommand(CommandKind.STATUS);
        if ("git log --oneline --all --decorate --graph".equals(raw)) return new GitCommand(CommandKind.LOG_GRAPH_ALL);
        if ("git diff".equals(raw)) return new GitCommand(CommandKind.DIFF);
        if ("git branch".equals(raw)) return new GitCommand(CommandKind.BRANCH);
        if ("git merge feature/profile-message".equals(raw)) return new GitCommand(CommandKind.MERGE_PROFILE_MESSAGE);
        if (("git add " + STAGE_FOUR_PATH).equals(raw)) return new GitCommand(CommandKind.ADD_PROFILE_MESSAGES);
        if ("git commit --no-edit".equals(raw)) return new GitCommand(CommandKind.COMMIT_NO_EDIT);
        if ("git commit -a --no-edit".equals(raw)) return new GitCommand(CommandKind.COMMIT_ALL_NO_EDIT);
        throw unsupportedSyntax();
    }
    private GitCommand parseStageFive(String raw) {
        if ("git status".equals(raw)) return new GitCommand(CommandKind.STATUS);
        if ("git log --oneline --all --decorate".equals(raw)) return new GitCommand(CommandKind.LOG_ONELINE_ALL_DECORATE);
        if ("git reflog".equals(raw)) return new GitCommand(CommandKind.REFLOG_HEAD);
        if (raw.matches("git show " + OBJECT_ID.pattern())) return new GitCommand(CommandKind.SHOW, raw.substring(9));
        if (raw.matches("git branch feature/payment-retry " + OBJECT_ID.pattern())) {
            return new GitCommand(CommandKind.CREATE_PAYMENT_RETRY_BRANCH, raw.substring("git branch feature/payment-retry ".length()));
        }
        if ("git switch feature/payment-retry".equals(raw)) return new GitCommand(CommandKind.SWITCH_PAYMENT_RETRY);
        if (raw.matches("git switch -c feature/payment-retry " + OBJECT_ID.pattern())) {
            return new GitCommand(CommandKind.SWITCH_CREATE_PAYMENT_RETRY,
                    raw.substring("git switch -c feature/payment-retry ".length()));
        }
        throw unsupportedSyntax();
    }
    private GitCommand parseTrainingOne(String raw) {
        GitCommand observation = parseTrainingObservation(raw, false);
        if (observation != null) return observation;
        if ("git add onboarding/intro.txt".equals(raw)) return new GitCommand(CommandKind.ADD_TRAINING_INTRO);
        if ("git commit -m complete-training-01".equals(raw)) return new GitCommand(CommandKind.COMMIT_TRAINING_ONE);
        throw unsupportedSyntax();
    }
    private GitCommand parseTrainingTwo(String raw) {
        GitCommand observation = parseTrainingObservation(raw, false);
        if (observation != null) return observation;
        if ("git restore --staged build/training-report.txt".equals(raw)) return new GitCommand(CommandKind.UNSTAGE_TRAINING_REPORT);
        if ("git add .gitignore".equals(raw)) return new GitCommand(CommandKind.ADD_TRAINING_IGNORE);
        if ("git add config/application-training.properties".equals(raw)) return new GitCommand(CommandKind.ADD_TRAINING_CONFIG);
        if ("git commit -m complete-training-02".equals(raw)) return new GitCommand(CommandKind.COMMIT_TRAINING_TWO);
        throw unsupportedSyntax();
    }
    private GitCommand parseTrainingThree(String raw) {
        GitCommand observation = parseTrainingObservation(raw, true);
        if (observation != null) return observation;
        if ("git switch -c feature/onboarding".equals(raw)) return new GitCommand(CommandKind.SWITCH_CREATE_TRAINING_BRANCH);
        if ("git switch feature/onboarding".equals(raw)) return new GitCommand(CommandKind.SWITCH_TRAINING_BRANCH);
        if ("git add docs/handoff.md".equals(raw)) return new GitCommand(CommandKind.ADD_TRAINING_HANDOFF);
        if ("git commit -m complete-training-03".equals(raw)) return new GitCommand(CommandKind.COMMIT_TRAINING_THREE);
        throw unsupportedSyntax();
    }
    private GitCommand parseTrainingObservation(String raw, boolean branchAllowed) {
        if ("git status".equals(raw)) return new GitCommand(CommandKind.STATUS);
        if ("git diff".equals(raw)) return new GitCommand(CommandKind.DIFF);
        if ("git diff --staged".equals(raw)) return new GitCommand(CommandKind.DIFF_STAGED);
        if ("git log --oneline".equals(raw)) return new GitCommand(CommandKind.LOG_ONELINE);
        if (branchAllowed && "git branch".equals(raw)) return new GitCommand(CommandKind.BRANCH);
        return null;
    }
    private void rejectUnsafeRaw(String raw) {
        if (raw == null || raw.length() > 512 || raw.indexOf('\n') >= 0 || raw.indexOf('\r') >= 0
                || raw.matches(".*[;|&<>`].*") || raw.contains("$()") || raw.contains("\"") || raw.contains("'")) {
            throw new StageInputException("入力形式を確認してください。改行やshell記号は使わず、1回に1つのGitコマンドを入力してください。必要に応じてヒントを確認してください。",
                    "INVALID_SYNTAX");
        }
    }
    private String exactAllowedObject(String input, StageRules.StageTargets targets, Set<String> displayed) {
        List<String> candidates = targets.allowedObjects().stream().filter(id -> id.startsWith(input)).toList();
        if (candidates.size() != 1 || !displayed.contains(candidates.getFirst().substring(0, 12))) {
            throw new StageInputException("そのcommit IDは確認済みの対象として扱えません。履歴を調査するか、必要に応じてヒントを確認してください。",
                    "OBJECT_NOT_ALLOWED");
        }
        return candidates.getFirst();
    }
    private StageInputException unsupportedSyntax() {
        return new StageInputException("入力した構文または引数は、このステージでは扱えません。必要に応じてヒントを確認してください。",
                "INVALID_SYNTAX");
    }
}
