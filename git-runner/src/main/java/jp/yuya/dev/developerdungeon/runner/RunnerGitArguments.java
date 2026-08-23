package jp.yuya.dev.developerdungeon.runner;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import jp.yuya.dev.developerdungeon.contract.GitCommand;

final class RunnerGitArguments {
    private static final String STAGE_FOUR_PATH = "src/main/resources/messages.properties";

    List<String> forPlayerCommand(String containerId, GitCommand command) {
        var arguments = commonPlayerPrefix(containerId);
        switch (command.kind()) {
            case STATUS -> arguments.addAll(List.of("status", "--short"));
            case LOG_ONELINE -> arguments.addAll(List.of("log", "--oneline", "--no-decorate", "--abbrev=12"));
            case LOG_ONELINE_ALL_DECORATE -> arguments.addAll(List.of("log", "--oneline", "--all", "--decorate", "--abbrev=12"));
            case BRANCH -> arguments.add("branch");
            case SHOW -> { arguments.addAll(List.of("show", "--no-ext-diff", "--no-textconv")); arguments.add(command.objectId()); }
            case SWITCH -> { arguments.add("switch"); arguments.add(command.branchName()); }
            case CHERRY_PICK -> { arguments.add("cherry-pick"); arguments.add(command.objectId()); }
            case RESET_HARD -> { arguments.addAll(List.of("reset", "--hard")); arguments.add(command.objectId()); }
            case REVERT_NO_EDIT -> { arguments.addAll(List.of("revert", "--no-edit")); arguments.add(command.objectId()); }
            case REVERT_NO_COMMIT -> { arguments.addAll(List.of("revert", "--no-commit")); arguments.add(command.objectId()); }
            case COMMIT_RESTORE_SETTINGS -> arguments.addAll(List.of("commit", "-m", "restore-required-settings"));
            case DIFF -> arguments.addAll(List.of("diff", "--no-ext-diff", "--no-textconv", "--"));
            case DIFF_STAGED -> arguments.addAll(List.of("diff", "--staged", "--no-ext-diff", "--no-textconv", "--"));
            case STASH_PUSH -> arguments.addAll(List.of("stash", "push"));
            case STASH_LIST -> arguments.addAll(List.of("stash", "list"));
            case STASH_POP -> arguments.addAll(List.of("stash", "pop"));
            case STASH_APPLY -> arguments.addAll(List.of("stash", "apply"));
            case STASH_DROP -> arguments.addAll(List.of("stash", "drop"));
            case LOG_GRAPH_ALL -> arguments.addAll(List.of("log", "--oneline", "--all", "--decorate", "--graph", "--abbrev=12"));
            case MERGE_PROFILE_MESSAGE -> arguments.addAll(List.of("merge", "--no-edit", "feature/profile-message"));
            case ADD_PROFILE_MESSAGES -> arguments.addAll(List.of("add", "--", STAGE_FOUR_PATH));
            case COMMIT_NO_EDIT -> arguments.addAll(List.of("commit", "--no-edit"));
            case COMMIT_ALL_NO_EDIT -> arguments.addAll(List.of("commit", "-a", "--no-edit"));
            case REFLOG_HEAD -> arguments.addAll(List.of("reflog", "show", "--format=%h%x09%gs", "--abbrev=12", "--max-count=8", "HEAD"));
            case CREATE_PAYMENT_RETRY_BRANCH -> arguments.addAll(List.of("branch", "feature/payment-retry", command.objectId()));
            case SWITCH_PAYMENT_RETRY -> arguments.addAll(List.of("switch", "feature/payment-retry"));
            case SWITCH_CREATE_PAYMENT_RETRY -> { arguments.addAll(List.of("switch", "-c", "feature/payment-retry")); arguments.add(command.objectId()); }
            case ADD_TRAINING_INTRO -> arguments.addAll(List.of("add", "--", "onboarding/intro.txt"));
            case COMMIT_TRAINING_ONE -> arguments.addAll(List.of("commit", "-m", "complete-training-01"));
            case UNSTAGE_TRAINING_REPORT -> arguments.addAll(List.of("restore", "--staged", "--", "build/training-report.txt"));
            case ADD_TRAINING_IGNORE -> arguments.addAll(List.of("add", "--", ".gitignore"));
            case ADD_TRAINING_CONFIG -> arguments.addAll(List.of("add", "--", "config/application-training.properties"));
            case COMMIT_TRAINING_TWO -> arguments.addAll(List.of("commit", "-m", "complete-training-02"));
            case SWITCH_CREATE_TRAINING_BRANCH -> arguments.addAll(List.of("switch", "-c", "feature/onboarding"));
            case SWITCH_TRAINING_BRANCH -> arguments.addAll(List.of("switch", "feature/onboarding"));
            case ADD_TRAINING_HANDOFF -> arguments.addAll(List.of("add", "--", "docs/handoff.md"));
            case COMMIT_TRAINING_THREE -> arguments.addAll(List.of("commit", "-m", "complete-training-03"));
        }
        return Collections.unmodifiableList(arguments);
    }

    private ArrayList<String> commonPlayerPrefix(String containerId) {
        var arguments = new ArrayList<>(List.of("exec", "--env", "HOME=/tmp/empty-home", "--env", "XDG_CONFIG_HOME=/tmp/empty-xdg",
                "--env", "GIT_CONFIG_NOSYSTEM=1", "--env", "GIT_CONFIG_GLOBAL=/dev/null", "--env", "GIT_TERMINAL_PROMPT=0",
                "--env", "GIT_ASKPASS=/bin/false", "--env", "GIT_ALLOW_PROTOCOL=", "--env", "GIT_PROTOCOL_FROM_USER=0",
                containerId, "/usr/bin/git", "-C", "/workspace",
                "-c", "core.hooksPath=/opt/empty-hooks", "-c", "core.attributesFile=/dev/null",
                "-c", "credential.helper=", "-c", "core.pager=cat", "-c", "core.editor=:",
                "-c", "protocol.file.allow=never", "-c", "user.name=Developer Dungeon Player",
                "-c", "user.email=player@developer-dungeon.invalid"));
        return arguments;
    }
}
