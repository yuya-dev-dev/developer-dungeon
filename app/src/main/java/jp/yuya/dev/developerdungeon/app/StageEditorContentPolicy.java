package jp.yuya.dev.developerdungeon.app;

import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;

final class StageEditorContentPolicy {
    private StageEditorContentPolicy() { }

    static String normalize(String content) {
        if (content == null) throw new IllegalArgumentException("ファイル内容を入力してください。");
        String normalized = content.replace("\r\n", "\n");
        if (normalized.indexOf('\r') >= 0 || normalized.codePoints().anyMatch(code ->
                Character.isISOControl(code) && code != '\n' && code != '\t')) {
            throw new IllegalArgumentException("ファイル内容に使用できない制御文字が含まれています。");
        }
        try {
            var encoded = StandardCharsets.UTF_8.newEncoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).encode(CharBuffer.wrap(normalized));
            if (encoded.remaining() > 2048) throw new IllegalArgumentException("ファイル内容はUTF-8で2048 byte以内にしてください。");
        } catch (CharacterCodingException exception) {
            throw new IllegalArgumentException("ファイル内容は正しいUTF-8文字列にしてください。", exception);
        }
        return normalized;
    }
}
