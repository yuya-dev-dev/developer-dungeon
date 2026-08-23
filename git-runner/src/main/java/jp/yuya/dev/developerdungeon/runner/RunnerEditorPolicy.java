package jp.yuya.dev.developerdungeon.runner;

import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.CharBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import jp.yuya.dev.developerdungeon.contract.RepositorySnapshot;
import jp.yuya.dev.developerdungeon.contract.StageFileKey;

final class RunnerEditorPolicy {
    private static final String STAGE_FOUR_PATH = "src/main/resources/messages.properties";
    private static final int MAX_CONTENT_BYTES = 2_048;

    void requireAllowedFile(String stageKey, StageFileKey fileKey) {
        if (!"STAGE-GIT-04".equals(stageKey) || fileKey != StageFileKey.PROFILE_MESSAGES) {
            throw new IllegalArgumentException("file is not allowed for this stage");
        }
    }

    void requireVersionToken(String versionToken) {
        if (versionToken == null || !versionToken.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("file version is invalid");
        }
    }

    void validateEditState(RepositorySnapshot snapshot) {
        var state = snapshot.stageFour();
        if (!"main".equals(snapshot.currentBranch()) || !snapshot.mergeInProgress() || snapshot.revertInProgress()
                || snapshot.cherryPickInProgress() || snapshot.rebaseInProgress()
                || !onlyStageFourPath(state.workingTreePaths()) || !onlyStageFourPath(state.indexPaths())
                || !onlyStageFourPath(state.unmergedPaths()) || !onlyStageFourPath(state.untrackedPaths())) {
            throw new IllegalArgumentException("stage file cannot be edited in the current repository state");
        }
    }

    NormalizedContent normalizePlayerContent(String content) {
        if (content == null) throw new IllegalArgumentException("file content is required");
        String normalized = content.replace("\r\n", "\n");
        return new NormalizedContent(normalized, validatedBytes(normalized, true));
    }

    byte[] validatedStoredBytes(String content) {
        return validatedBytes(content, false);
    }

    String versionToken(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private boolean onlyStageFourPath(List<String> paths) {
        return paths.stream().allMatch(STAGE_FOUR_PATH::equals);
    }

    private byte[] validatedBytes(String content, boolean playerInput) {
        if (content == null || content.indexOf('\r') >= 0 || content.codePoints().anyMatch(code ->
                Character.isISOControl(code) && code != '\n' && code != '\t')) {
            throw new IllegalArgumentException(playerInput
                    ? "file content contains an invalid control character"
                    : "stage file content is invalid");
        }
        try {
            CharsetEncoder encoder = StandardCharsets.UTF_8.newEncoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT);
            var encoded = encoder.encode(CharBuffer.wrap(content));
            byte[] bytes = new byte[encoded.remaining()];
            encoded.get(bytes);
            if (bytes.length > MAX_CONTENT_BYTES) throw new IllegalArgumentException("file content is too large");
            return bytes;
        } catch (CharacterCodingException exception) {
            throw new IllegalArgumentException("file content is not valid UTF-8", exception);
        }
    }

    record NormalizedContent(String content, byte[] bytes) {
        NormalizedContent {
            bytes = bytes.clone();
        }
        @Override public byte[] bytes() { return bytes.clone(); }
    }
}
