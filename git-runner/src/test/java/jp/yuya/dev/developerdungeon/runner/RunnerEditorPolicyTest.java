package jp.yuya.dev.developerdungeon.runner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import jp.yuya.dev.developerdungeon.contract.RepositorySnapshot;
import jp.yuya.dev.developerdungeon.contract.StageFileKey;
import org.junit.jupiter.api.Test;

class RunnerEditorPolicyTest {
    private final RunnerEditorPolicy policy = new RunnerEditorPolicy();

    @Test
    void allowsOnlyTheFixedStageFourFile() {
        assertThatCode(() -> policy.requireAllowedFile("STAGE-GIT-04", StageFileKey.PROFILE_MESSAGES))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> policy.requireAllowedFile("STAGE-GIT-03", StageFileKey.PROFILE_MESSAGES))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("file is not allowed for this stage");
        assertThatThrownBy(() -> policy.requireAllowedFile("STAGE-GIT-04", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("file is not allowed for this stage");
    }

    @Test
    void validatesTheExactVersionTokenShape() {
        assertThatCode(() -> policy.requireVersionToken("a".repeat(64))).doesNotThrowAnyException();
        for (String invalid : List.of("", "a".repeat(63), "A".repeat(64), "g".repeat(64))) {
            assertThatThrownBy(() -> policy.requireVersionToken(invalid))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("file version is invalid");
        }
        assertThatThrownBy(() -> policy.requireVersionToken(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("file version is invalid");
    }

    @Test
    void normalizesCrLfButRejectsBareCarriageReturnsAndControls() {
        var normalized = policy.normalizePlayerContent("first\r\nsecond\n\tvalue");
        assertThat(normalized.content()).isEqualTo("first\nsecond\n\tvalue");
        assertThat(normalized.bytes()).containsExactly("first\nsecond\n\tvalue".getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> policy.normalizePlayerContent("first\rsecond"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("file content contains an invalid control character");
        assertThatThrownBy(() -> policy.normalizePlayerContent("first\u0000second"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("file content contains an invalid control character");
        assertThatThrownBy(() -> policy.normalizePlayerContent(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("file content is required");
    }

    @Test
    void enforcesTheUtf8ByteLimitForAsciiAndMultibyteText() {
        assertThat(policy.normalizePlayerContent("a".repeat(2_048)).bytes()).hasSize(2_048);
        assertThatThrownBy(() -> policy.normalizePlayerContent("a".repeat(2_049)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("file content is too large");
        assertThat(policy.normalizePlayerContent("あ".repeat(682)).bytes()).hasSize(2_046);
        assertThatThrownBy(() -> policy.normalizePlayerContent("あ".repeat(683)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("file content is too large");
    }

    @Test
    void preservesStoredContentAndMalformedUtf8ErrorContracts() {
        assertThatThrownBy(() -> policy.validatedStoredBytes("stored\r\ncontent"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("stage file content is invalid");
        assertThatThrownBy(() -> policy.normalizePlayerContent("\uD800"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("file content is not valid UTF-8")
                .hasCauseInstanceOf(CharacterCodingException.class);
    }

    @Test
    void computesTheExactSha256TokenAndDefensivelyCopiesBytes() {
        byte[] bytes = "abc".getBytes(StandardCharsets.UTF_8);
        assertThat(policy.versionToken(bytes))
                .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");

        var normalized = policy.normalizePlayerContent("abc");
        byte[] first = normalized.bytes();
        first[0] = 'z';
        assertThat(normalized.bytes()).containsExactly((byte) 'a', (byte) 'b', (byte) 'c');
    }

    @Test
    void permitsEditingOnlyDuringTheFixedStageFourMergeState() {
        assertThatCode(() -> policy.validateEditState(snapshot("main", true, List.of(), false)))
                .doesNotThrowAnyException();
        assertThatCode(() -> policy.validateEditState(snapshot("main", true,
                List.of("src/main/resources/messages.properties"), false)))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> policy.validateEditState(snapshot("feature/profile-message", true, List.of(), false)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("stage file cannot be edited in the current repository state");
        assertThatThrownBy(() -> policy.validateEditState(snapshot("main", false, List.of(), false)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("stage file cannot be edited in the current repository state");
        assertThatThrownBy(() -> policy.validateEditState(snapshot("main", true, List.of("other.txt"), false)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("stage file cannot be edited in the current repository state");
        assertThatThrownBy(() -> policy.validateEditState(snapshot("main", true, List.of(), true)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("stage file cannot be edited in the current repository state");
    }

    private RepositorySnapshot snapshot(String branch, boolean merging, List<String> paths, boolean reverting) {
        var stageFour = new RepositorySnapshot.StageFourState("", "", "", "", "", "", "",
                paths, paths, paths, paths);
        return new RepositorySnapshot("a".repeat(40), "b".repeat(40), "", List.of(), false, reverting,
                List.of(), branch, "", "", false, merging, false, RepositorySnapshot.StageThreeState.empty(),
                stageFour, RepositorySnapshot.StageFiveState.empty(), RepositorySnapshot.TrainingState.empty());
    }
}
