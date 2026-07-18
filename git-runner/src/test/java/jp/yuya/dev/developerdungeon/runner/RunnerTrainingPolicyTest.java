package jp.yuya.dev.developerdungeon.runner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import jp.yuya.dev.developerdungeon.contract.CommandKind;
import jp.yuya.dev.developerdungeon.contract.GitCommand;
import org.junit.jupiter.api.Test;

class RunnerTrainingPolicyTest {
    private final RunnerWorkspaceService service = new RunnerWorkspaceService(null, null, null);

    @Test void rejectsCommandKindsThatBelongToAnotherTraining() {
        assertThatCode(() -> service.validateTrainingCommand("TRAINING-GIT-01", CommandKind.ADD_TRAINING_INTRO))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> service.validateTrainingCommand("TRAINING-GIT-01", CommandKind.ADD_TRAINING_CONFIG))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.validateTrainingCommand("TRAINING-GIT-02", CommandKind.SWITCH_CREATE_TRAINING_BRANCH))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.validateTrainingCommand("TRAINING-GIT-03", CommandKind.COMMIT_TRAINING_ONE))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.validateTrainingCommand("TRAINING-GIT-99", CommandKind.STATUS))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test void buildsOnlyFixedArgvForEveryTrainingMutation() {
        assertSuffix(CommandKind.ADD_TRAINING_INTRO, "add", "--", "onboarding/intro.txt");
        assertSuffix(CommandKind.COMMIT_TRAINING_ONE, "commit", "-m", "complete-training-01");
        assertSuffix(CommandKind.UNSTAGE_TRAINING_REPORT, "restore", "--staged", "--", "build/training-report.txt");
        assertSuffix(CommandKind.ADD_TRAINING_IGNORE, "add", "--", ".gitignore");
        assertSuffix(CommandKind.ADD_TRAINING_CONFIG, "add", "--", "config/application-training.properties");
        assertSuffix(CommandKind.COMMIT_TRAINING_TWO, "commit", "-m", "complete-training-02");
        assertSuffix(CommandKind.SWITCH_CREATE_TRAINING_BRANCH, "switch", "-c", "feature/onboarding");
        assertSuffix(CommandKind.SWITCH_TRAINING_BRANCH, "switch", "feature/onboarding");
        assertSuffix(CommandKind.ADD_TRAINING_HANDOFF, "add", "--", "docs/handoff.md");
        assertSuffix(CommandKind.COMMIT_TRAINING_THREE, "commit", "-m", "complete-training-03");
    }

    private void assertSuffix(CommandKind kind, String... expected) {
        List<String> arguments = service.gitArguments("fixed-container", new GitCommand(kind));
        assertThat(arguments.subList(arguments.size() - expected.length, arguments.size()))
                .containsExactly(expected);
    }
}
