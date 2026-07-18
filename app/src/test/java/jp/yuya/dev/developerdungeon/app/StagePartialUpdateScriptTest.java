package jp.yuya.dev.developerdungeon.app;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class StagePartialUpdateScriptTest {
    @Test void validatesTheWholeSameStageResponseBeforeReplacingStableRegions() throws IOException {
        String script;
        try (var input = getClass().getResourceAsStream("/static/stage-partial-update.js")) {
            if (input == null) throw new AssertionError("stage-partial-update.js was not found");
            script = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }

        assertThat(script).contains("stage-header", "stage-sidebar-state", "stage-repository", "stage-workspace",
                "stage-clear-dialogue", "response.ok", "text/html", "window.location.origin", "dataset.stageKey",
                "querySelector(\"script\")", "new DOMParser()", "document.importNode", "replaceWith",
                "credentials: \"same-origin\"", "pending")
                .doesNotContain("innerHTML", "eval(", "location.reload(", "behavior: \"smooth\"");
    }
}
