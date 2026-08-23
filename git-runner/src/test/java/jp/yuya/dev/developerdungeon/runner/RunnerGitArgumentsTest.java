package jp.yuya.dev.developerdungeon.runner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import jp.yuya.dev.developerdungeon.contract.CommandKind;
import jp.yuya.dev.developerdungeon.contract.GitCommand;
import org.junit.jupiter.api.Test;

class RunnerGitArgumentsTest {
    private static final String CONTAINER = "fixed-container";
    private static final String OBJECT = "0123456789abcdef0123456789abcdef01234567";
    private static final String BRANCH = "feature/profile";
    private final RunnerGitArguments arguments = new RunnerGitArguments();

    @Test
    void buildsTheExactFixedArgvForEveryCommandKind() {
        Map<CommandKind, List<String>> suffixes = expectedSuffixes();

        assertThat(suffixes).hasSize(CommandKind.values().length);
        for (CommandKind kind : CommandKind.values()) {
            List<String> expected = new ArrayList<>(commonPrefix());
            expected.addAll(suffixes.get(kind));

            assertThat(arguments.forPlayerCommand(CONTAINER, new GitCommand(kind, OBJECT, BRANCH)))
                    .as(kind.name())
                    .containsExactlyElementsOf(expected);
        }
    }

    @Test
    void returnsAnIndependentListForEachCall() {
        List<String> first = arguments.forPlayerCommand(CONTAINER, new GitCommand(CommandKind.STATUS));
        List<String> second = arguments.forPlayerCommand(CONTAINER, new GitCommand(CommandKind.STATUS));

        assertThat(first).isNotSameAs(second);
        assertThatThrownBy(() -> first.set(first.size() - 1, "changed"))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThat(second.getLast()).isEqualTo("--short");
    }

    private List<String> commonPrefix() {
        return List.of("exec", "--env", "HOME=/tmp/empty-home", "--env", "XDG_CONFIG_HOME=/tmp/empty-xdg",
                "--env", "GIT_CONFIG_NOSYSTEM=1", "--env", "GIT_CONFIG_GLOBAL=/dev/null", "--env", "GIT_TERMINAL_PROMPT=0",
                "--env", "GIT_ASKPASS=/bin/false", "--env", "GIT_ALLOW_PROTOCOL=", "--env", "GIT_PROTOCOL_FROM_USER=0",
                CONTAINER, "/usr/bin/git", "-C", "/workspace",
                "-c", "core.hooksPath=/opt/empty-hooks", "-c", "core.attributesFile=/dev/null",
                "-c", "credential.helper=", "-c", "core.pager=cat", "-c", "core.editor=:",
                "-c", "protocol.file.allow=never", "-c", "user.name=Developer Dungeon Player",
                "-c", "user.email=player@developer-dungeon.invalid");
    }

    private Map<CommandKind, List<String>> expectedSuffixes() {
        Map<CommandKind, List<String>> suffixes = new EnumMap<>(CommandKind.class);
        suffixes.put(CommandKind.STATUS, List.of("status", "--short"));
        suffixes.put(CommandKind.LOG_ONELINE, List.of("log", "--oneline", "--no-decorate", "--abbrev=12"));
        suffixes.put(CommandKind.LOG_ONELINE_ALL_DECORATE, List.of("log", "--oneline", "--all", "--decorate", "--abbrev=12"));
        suffixes.put(CommandKind.BRANCH, List.of("branch"));
        suffixes.put(CommandKind.SHOW, List.of("show", "--no-ext-diff", "--no-textconv", OBJECT));
        suffixes.put(CommandKind.SWITCH, List.of("switch", BRANCH));
        suffixes.put(CommandKind.CHERRY_PICK, List.of("cherry-pick", OBJECT));
        suffixes.put(CommandKind.RESET_HARD, List.of("reset", "--hard", OBJECT));
        suffixes.put(CommandKind.REVERT_NO_EDIT, List.of("revert", "--no-edit", OBJECT));
        suffixes.put(CommandKind.REVERT_NO_COMMIT, List.of("revert", "--no-commit", OBJECT));
        suffixes.put(CommandKind.COMMIT_RESTORE_SETTINGS, List.of("commit", "-m", "restore-required-settings"));
        suffixes.put(CommandKind.DIFF, List.of("diff", "--no-ext-diff", "--no-textconv", "--"));
        suffixes.put(CommandKind.DIFF_STAGED, List.of("diff", "--staged", "--no-ext-diff", "--no-textconv", "--"));
        suffixes.put(CommandKind.STASH_PUSH, List.of("stash", "push"));
        suffixes.put(CommandKind.STASH_LIST, List.of("stash", "list"));
        suffixes.put(CommandKind.STASH_POP, List.of("stash", "pop"));
        suffixes.put(CommandKind.STASH_APPLY, List.of("stash", "apply"));
        suffixes.put(CommandKind.STASH_DROP, List.of("stash", "drop"));
        suffixes.put(CommandKind.LOG_GRAPH_ALL, List.of("log", "--oneline", "--all", "--decorate", "--graph", "--abbrev=12"));
        suffixes.put(CommandKind.MERGE_PROFILE_MESSAGE, List.of("merge", "--no-edit", "feature/profile-message"));
        suffixes.put(CommandKind.ADD_PROFILE_MESSAGES, List.of("add", "--", "src/main/resources/messages.properties"));
        suffixes.put(CommandKind.COMMIT_NO_EDIT, List.of("commit", "--no-edit"));
        suffixes.put(CommandKind.COMMIT_ALL_NO_EDIT, List.of("commit", "-a", "--no-edit"));
        suffixes.put(CommandKind.REFLOG_HEAD, List.of("reflog", "show", "--format=%h%x09%gs", "--abbrev=12", "--max-count=8", "HEAD"));
        suffixes.put(CommandKind.CREATE_PAYMENT_RETRY_BRANCH, List.of("branch", "feature/payment-retry", OBJECT));
        suffixes.put(CommandKind.SWITCH_PAYMENT_RETRY, List.of("switch", "feature/payment-retry"));
        suffixes.put(CommandKind.SWITCH_CREATE_PAYMENT_RETRY, List.of("switch", "-c", "feature/payment-retry", OBJECT));
        suffixes.put(CommandKind.ADD_TRAINING_INTRO, List.of("add", "--", "onboarding/intro.txt"));
        suffixes.put(CommandKind.COMMIT_TRAINING_ONE, List.of("commit", "-m", "complete-training-01"));
        suffixes.put(CommandKind.UNSTAGE_TRAINING_REPORT, List.of("restore", "--staged", "--", "build/training-report.txt"));
        suffixes.put(CommandKind.ADD_TRAINING_IGNORE, List.of("add", "--", ".gitignore"));
        suffixes.put(CommandKind.ADD_TRAINING_CONFIG, List.of("add", "--", "config/application-training.properties"));
        suffixes.put(CommandKind.COMMIT_TRAINING_TWO, List.of("commit", "-m", "complete-training-02"));
        suffixes.put(CommandKind.SWITCH_CREATE_TRAINING_BRANCH, List.of("switch", "-c", "feature/onboarding"));
        suffixes.put(CommandKind.SWITCH_TRAINING_BRANCH, List.of("switch", "feature/onboarding"));
        suffixes.put(CommandKind.ADD_TRAINING_HANDOFF, List.of("add", "--", "docs/handoff.md"));
        suffixes.put(CommandKind.COMMIT_TRAINING_THREE, List.of("commit", "-m", "complete-training-03"));
        return suffixes;
    }
}
