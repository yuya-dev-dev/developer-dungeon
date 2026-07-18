package jp.yuya.dev.developerdungeon.app;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class StageDialogueStyleTest {
    @Test void hiddenDialogueSceneDoesNotOverrideTheHtmlHiddenAttribute() throws IOException {
        try (var input = getClass().getResourceAsStream("/static/stage.css")) {
            if (input == null) throw new AssertionError("stage.css was not found");
            String css = new String(input.readAllBytes(), StandardCharsets.UTF_8);

            assertThat(css).contains(".dialogue-scene[hidden] { display: none; }");
        }
    }
}
